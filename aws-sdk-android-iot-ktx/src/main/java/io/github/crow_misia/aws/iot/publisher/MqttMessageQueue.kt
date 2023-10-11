/**
 * Copyright (C) 2023 Zenichi Amano.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.publish
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * MQTT送信メッセージキュー.
 */
interface MqttMessageQueue {
    /**
     * Flowを取得.
     */
    suspend fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage>

    /**
     * メッセージ送信.
     */
    fun send(message: MqttMessage)

    /**
     * キューを閉じる.
     *
     * @param timeout タイムアウト時間
     */
    suspend fun close(timeout: Duration)

    companion object {
        fun createMessageQueue(clock: Clock, messageExpired: Duration): MqttMessageQueue {
            return ChannelMqttMessageQueue(clock, messageExpired)
        }

        fun wrap(block: suspend FlowCollector<MqttMessage>.() -> Unit): MqttMessageQueue {
            return FlowMqttMessageQueue(flow(block))
        }
    }
}

internal class FlowMqttMessageQueue(
    private val parentFlow: Flow<MqttMessage>,
) : MqttMessageQueue {
    override suspend fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        return parentFlow.onEach {
            client.publish(it)
        }
    }

    override fun send(message: MqttMessage) {
        throw UnsupportedOperationException()
    }

    override suspend fun close(timeout: Duration) {
        // nop.
    }
}

internal class ChannelMqttMessageQueue(
    private val clock: Clock,
    private val messageExpired: Duration,
    private val capacity: Int = Channel.UNLIMITED,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
) : MqttMessageQueue {
    private var queue = Channel<MqttMessage>(
        capacity = Channel.RENDEZVOUS,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val errorBoundary = CopyOnWriteArrayList<ErrorMqttMessage>()

    override suspend fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        return createQueue().onEach { message ->
            runCatching {
                client.publish(message)
            }.onFailure {
                errorBoundary.add(ErrorMqttMessage.wrap(message, clock))
            }
        }.onStart {
            val limitTime = clock.millis() - messageExpired.inWholeMilliseconds
            errorBoundary.removeAll {
                if (it.timestamp < limitTime) {
                    true
                } else {
                    queue.trySend(it).isSuccess
                }
            }
        }
    }

    override fun send(message: MqttMessage) {
        queue.trySend(message).onFailure {
            errorBoundary.add(ErrorMqttMessage.wrap(message, clock))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override suspend fun close(timeout: Duration) {
        withTimeoutOrNull(timeout) {
            while (!queue.isClosedForSend && !queue.isEmpty) {
                delay(100.milliseconds)
            }
        }
        queue.close()
    }

    private fun createQueue(): Flow<MqttMessage> {
        return Channel<MqttMessage>(
            capacity = capacity,
            onBufferOverflow = onBufferOverflow,
        ).also { channel ->
            queue = channel
        }.consumeAsFlow()
    }
}

/**
 * エラー発生MQTTメッセージ.
 *
 * @property timestamp 送信タイムスタンプ(ms)
 */
private class ErrorMqttMessage private constructor(
    private val delegated: MqttMessage,
    val timestamp: Long,
): MqttMessage by delegated {
    companion object {
        fun wrap(message: MqttMessage, clock: Clock): ErrorMqttMessage {
            if (message is ErrorMqttMessage) {
                return message
            }
            return ErrorMqttMessage(message, clock.millis())
        }
    }
}
