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
import kotlinx.coroutines.withTimeout
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
    suspend fun close(timeout: Duration): Result<Unit>

    companion object {
        fun createMessageQueue(
            clock: Clock,
            messageExpired: Duration,
        ): MqttMessageQueue {
            return ChannelMqttMessageQueue(
                clock = clock,
                messageExpired = messageExpired,
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

    override suspend fun close(timeout: Duration): Result<Unit> {
        return Result.success(Unit)
    }
}

internal class ChannelMqttMessageQueue(
    private val clock: Clock,
    private val messageExpired: Duration,
) : MqttMessageQueue {
    private var isSending = false
    private val errorBoundary = CopyOnWriteArrayList<ErrorMqttMessage>()
    private var channel: Channel<MqttMessage> = createErrorBoundaryChannel()

    private fun createChannel(): Channel<MqttMessage> {
        return Channel(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.SUSPEND) {
            errorBoundary.add(ErrorMqttMessage.wrap(it) { clock.millis() })
        }
    }

    private fun createErrorBoundaryChannel(): Channel<MqttMessage> {
        return createChannel().also {
            // 送信されたメッセージは全てerrorBoundaryに格納するため、チャンネルを閉じる
            it.close()
        }
    }

    override suspend fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        channel.close()

        val channel = createChannel().also {
            this.channel = it
        }
        return channel.consumeAsFlow()
            .onStart {
                if (errorBoundary.isNotEmpty()) {
                    val limitTime = clock.millis() - messageExpired.inWholeMilliseconds
                    val copyList = ArrayList(errorBoundary)
                    errorBoundary.clear()
                    copyList.filter { it.timestamp >= limitTime }
                        .onEach { emit(it) }
                }
            }
            .onEach {
                client.publishWithError(it)
            }
    }

    private suspend fun AWSIotMqttManager.publishWithError(message: MqttMessage) {
        runCatching {
            isSending = true
            publish(message)
        }.fold(
            onSuccess = { isSending = false },
            onFailure = {
                isSending = false
                errorBoundary.add(ErrorMqttMessage.wrap(message) { clock.millis() })
                throw it
            },
        )
    }

    override suspend fun send(message: MqttMessage) {
        runCatching {
            channel.send(message)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun close(timeout: Duration): Result<Unit> {
        return runCatching {
            withTimeout(timeout) {
                while (isSending || !channel.isEmpty) {
                    delay(100.milliseconds)
                }
            }
        }.onSuccess {
            channel.close()
            channel = createErrorBoundaryChannel()
        }.onFailure {
            channel.close(it)
            channel = createErrorBoundaryChannel()
        }
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
