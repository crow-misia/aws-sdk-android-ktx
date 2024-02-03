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
    suspend fun send(message: MqttMessage)

    /**
     * キューを閉じる.
     *
     * @param timeout タイムアウト時間
     */
    suspend fun close(timeout: Duration)

    companion object {
        fun createMessageQueue(
            clock: Clock,
            messageExpired: Duration,
            capacity: Int = Channel.UNLIMITED,
            onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
        ): MqttMessageQueue {
            return ChannelMqttMessageQueue(
                clock = clock,
                messageExpired = messageExpired,
                capacity = capacity,
                onBufferOverflow = onBufferOverflow,
            )
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

    override suspend fun send(message: MqttMessage) {
        throw UnsupportedOperationException()
    }

    override suspend fun close(timeout: Duration) {
        // nop.
    }
}

internal class ChannelMqttMessageQueue(
    private val clock: Clock,
    private val messageExpired: Duration,
    capacity: Int = Channel.UNLIMITED,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
) : MqttMessageQueue {
    private var isBinded = false
    private val errorBoundary = CopyOnWriteArrayList<ErrorMqttMessage>()
    private val channel =
        Channel<MqttMessage>(capacity = capacity, onBufferOverflow = onBufferOverflow) {
            errorBoundary.add(ErrorMqttMessage.wrap(it) { clock.millis() })
        }
    private var parentFlow = channel.consumeAsFlow()

    override suspend fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        if (!isBinded) {
            isBinded = true
            parentFlow = parentFlow.onStart {
                if (errorBoundary.isNotEmpty()) {
                    val limitTime = clock.millis() - messageExpired.inWholeMilliseconds
                    val copyList = ArrayList(errorBoundary)
                    errorBoundary.clear()
                    copyList.filter { it.timestamp >= limitTime }
                        .onEach { emit(it) }
                }
            }.onEach {
                client.publishWithError(it).getOrThrow()
            }
        }
        return parentFlow
    }

    private suspend fun AWSIotMqttManager.publishWithError(message: MqttMessage): Result<*> {
        return runCatching {
            publish(message)
        }.onFailure {
            errorBoundary.add(ErrorMqttMessage.wrap(message) { clock.millis() })
        }
    }

    override suspend fun send(message: MqttMessage) {
        runCatching {
            channel.send(message)
        }.onFailure {
            errorBoundary.add(ErrorMqttMessage.wrap(message) { clock.millis() })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override suspend fun close(timeout: Duration) {
        withTimeoutOrNull(timeout) {
            while (!channel.isClosedForSend && !channel.isEmpty) {
                delay(100.milliseconds)
            }
        }
        channel.close()
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
) : MqttMessage by delegated {
    companion object {
        inline fun wrap(message: MqttMessage, getCurrentTime: () -> Long): ErrorMqttMessage {
            return if (message is ErrorMqttMessage) {
                message
            } else ErrorMqttMessage(message, getCurrentTime())
        }
    }
}
