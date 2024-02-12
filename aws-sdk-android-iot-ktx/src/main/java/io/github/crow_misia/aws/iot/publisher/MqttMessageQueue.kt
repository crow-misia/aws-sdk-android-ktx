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
import io.github.crow_misia.aws.iot.publishDefaultTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * MQTT送信メッセージキュー.
 */
interface MqttMessageQueue {
    /**
     * Flowを取得.
     */
    suspend fun asFlow(client: AWSIotMqttManager, publishTimeout: Duration = publishDefaultTimeout): Flow<MqttMessage>

    /**
     * メッセージ送信.
     */
    suspend fun send(message: MqttMessage)

    /**
     * キューが空になるまで待つ.
     *
     * @param timeout タイムアウト時間
     */
    suspend fun awaitUntilEmpty(timeout: Duration): Result<Unit>

    companion object {
        fun createMessageQueue(
            clock: Clock,
            messageExpired: Duration,
            pollInterval: Duration = 250.milliseconds,
        ): MqttMessageQueue {
            return ChannelMqttMessageQueue(
                clock = clock,
                messageExpired = messageExpired,
                pollInterval = pollInterval,
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
    override suspend fun asFlow(client: AWSIotMqttManager, publishTimeout: Duration): Flow<MqttMessage> {
        return parentFlow.onEach {
            client.publish(it, publishTimeout)
        }
    }

    override suspend fun send(message: MqttMessage) {
        throw UnsupportedOperationException()
    }

    override suspend fun awaitUntilEmpty(timeout: Duration): Result<Unit> {
        return Result.success(Unit)
    }
}

internal class ChannelMqttMessageQueue(
    private val clock: Clock,
    private val messageExpired: Duration,
    private val pollInterval: Duration,
) : MqttMessageQueue {
    private val messageQueue = ConcurrentLinkedQueue<MqttQueueMessage>()
    private val sendingCount: AtomicInteger = AtomicInteger(0)

    override suspend fun asFlow(client: AWSIotMqttManager, publishTimeout: Duration): Flow<MqttMessage> = channelFlow {
        sendingCount.set(0)

        while (true) {
            if (messageQueue.isNotEmpty()) {
                val limitTime = clock.millis() - messageExpired.inWholeMilliseconds
                var message: MqttQueueMessage?
                do {
                    sendingCount.incrementAndGet()
                    message = messageQueue.poll()
                    message?.let {
                        if (it.timestamp >= limitTime) {
                            send(it)
                        } else null
                    } ?: run {
                        sendingCount.updateAndGet { maxOf(it - 1, 0) }
                    }
                } while (message != null)
            }
            delay(pollInterval)
        }
    }.onEach { message ->
        runCatching {
            client.publish(message, publishTimeout)
            sendingCount.updateAndGet { maxOf(it - 1, 0) }
        }.onFailure {
            messageQueue.add(message)
            sendingCount.updateAndGet { maxOf(it - 1, 0) }
        }.getOrThrow()
    }

    override suspend fun send(message: MqttMessage) {
        messageQueue.add(MqttQueueMessage.wrap(message) { clock.millis() })
    }

    override suspend fun awaitUntilEmpty(timeout: Duration): Result<Unit> {
        return runCatching {
            withTimeout(timeout) {
                while (sendingCount.get() > 0 || messageQueue.isNotEmpty()) {
                    delay(pollInterval)
                }
            }
        }
    }
}

/**
 * キューイングMQTTメッセージ.
 *
 * @property timestamp 送信タイムスタンプ(ms)
 */
private class MqttQueueMessage private constructor(
    private val delegated: MqttMessage,
    val timestamp: Long,
) : MqttMessage by delegated {
    companion object {
        inline fun wrap(message: MqttMessage, getCurrentTime: () -> Long): MqttQueueMessage {
            return if (message is MqttQueueMessage) {
                message
            } else MqttQueueMessage(message, getCurrentTime())
        }
    }
}
