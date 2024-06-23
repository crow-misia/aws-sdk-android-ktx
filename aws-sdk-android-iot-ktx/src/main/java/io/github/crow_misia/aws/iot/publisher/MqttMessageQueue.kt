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

import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.publish
import io.github.crow_misia.aws.iot.publishDefaultTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * MQTT送信メッセージキュー.
 */
interface MqttMessageQueue {
    /**
     * Flowを取得.
     */
    fun asFlow(
        client: AWSIotMqttManager,
        publishTimeout: Duration = publishDefaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ): Flow<MqttMessage>

    /**
     * メッセージ送信.
     */
    suspend fun send(messages: List<MqttMessage>)

    /**
     * メッセージ送信.
     */
    suspend fun send(vararg messages: MqttMessage) {
        send(messages.toList())
    }

    /**
     * キューが空になるまで待つ.
     *
     * @param timeout タイムアウト時間
     */
    suspend fun awaitUntilEmpty(timeout: Duration): Result<Unit>

    companion object {
        fun createMessageQueue(
            clock: Clock = Clock.System,
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
    override fun asFlow(
        client: AWSIotMqttManager,
        publishTimeout: Duration,
        context: CoroutineContext,
    ): Flow<MqttMessage> {
        return parentFlow.onEach {
            client.publish(it, publishTimeout)
        }
    }

    override suspend fun send(messages: List<MqttMessage>) {
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
    private val messageCount: AtomicInteger = AtomicInteger(0)

    override fun asFlow(
        client: AWSIotMqttManager,
        publishTimeout: Duration,
        context: CoroutineContext,
    ): Flow<MqttMessage> = channelFlow {
        do {
            val message = messageQueue.poll()
            if (message == null) {
                delay(pollInterval)
                continue
            }
            val limitTime = clock.now() - messageExpired
            if (message.timestamp >= limitTime.epochMilliseconds) {
                withContext(context) {
                    runCatching {
                        client.publish(message, publishTimeout)
                    }
                }.onSuccess {
                    messageCount.decrementAndGet()
                    send(message)
                }.onFailure { _ ->
                    messageQueue.add(message)
                }.getOrThrow()
            } else {
                messageCount.decrementAndGet()
            }
        } while (true)
    }

    override suspend fun send(messages: List<MqttMessage>) {
        messageCount.addAndGet(messages.size)
        messageQueue.addAll(messages.map {
            MqttQueueMessage.wrap(it) { clock.now().epochMilliseconds }
        })
    }

    override suspend fun awaitUntilEmpty(timeout: Duration): Result<Unit> {
        return runCatching {
            withTimeout(timeout) {
                while (messageCount.get() > 0) {
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
