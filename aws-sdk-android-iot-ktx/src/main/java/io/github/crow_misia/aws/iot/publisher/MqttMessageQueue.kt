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

import androidx.annotation.VisibleForTesting
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.until
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedList
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
     * @return キューが空になった場合、true. タイムアウトになった場合、false
     */
    suspend fun awaitUntilEmpty(timeout: Duration): Boolean

    companion object {
        fun createMessageQueue(
            clock: Clock = Clock.System,
            messageExpired: Duration,
            pollInterval: Duration = 250.milliseconds,
            retainedPendingPeriod: Duration = 1.seconds
        ): MqttMessageQueue {
            return ChannelMqttMessageQueue(
                clock = clock,
                messageExpired = messageExpired,
                pollInterval = pollInterval,
                retainedPendingPeriod = retainedPendingPeriod,
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

    override suspend fun awaitUntilEmpty(timeout: Duration): Boolean {
        return true
    }
}

internal class ChannelMqttMessageQueue(
    private val clock: Clock,
    private val messageExpired: Duration,
    private val pollInterval: Duration,
    private val retainedPendingPeriod: Duration,
) : MqttMessageQueue {
    private val retainedTopicLastTime = mutableMapOf<TopicName, Instant>()
    private val messageQueue = LinkedList<MqttQueueMessage>()
    private var messageCount = 0
    private val mutex = Mutex()

    override fun asFlow(
        client: AWSIotMqttManager,
        publishTimeout: Duration,
        context: CoroutineContext,
    ): Flow<MqttMessage> = channelFlow {
        do {
            val currentTime = clock.now()
            val limitTime = currentTime - messageExpired
            val message = mutex.withLock {
                var index = 0

                while (index < messageQueue.size) {
                    // 有効期限切れではないメッセージを取り出す
                    val tmp = messageQueue.removeAt(index)
                    if (tmp.timestamp < limitTime) {
                        messageCount--
                    } else if (tmp.retainMode.isRetained) {
                        // RETAINメッセージの場合、前回送信時間から経過していない場合、保留する
                        val topicName = tmp.topicName
                        val canSend = retainedTopicLastTime[topicName]?.let { lastSendTime ->
                            lastSendTime.until(currentTime) >= retainedPendingPeriod
                        } != false
                        if (canSend) {
                            retainedTopicLastTime[topicName] = currentTime
                            return@withLock tmp
                        }
                        index++
                        messageQueue.addFirst(tmp)
                    } else {
                        return@withLock tmp
                    }
                }
                return@withLock null
            }
            if (message == null) {
                delay(pollInterval)
                continue
            }
            withContext(context) {
                runCatching {
                    client.publish(message, publishTimeout)
                }
            }.onSuccess {
                mutex.withLock {
                    messageCount--
                }
                send(message)
            }.onFailure { _ ->
                mutex.withLock {
                    messageQueue.push(message)
                }
            }.getOrThrow()
        } while (true)
    }

    override suspend fun send(messages: List<MqttMessage>) {
        mutex.withLock {
            messages.forEach {
                val topicName = it.topicName
                if (it.retainMode == RetainMode.OVERWRITE) {
                    // 同じトピックへ送信するメッセージを削除する
                    val each = messageQueue.iterator()
                    while (each.hasNext()) {
                        val tmp = each.next()
                        if (tmp.topicName == topicName) {
                            each.remove()
                            messageCount--
                        }
                    }
                }
                val wrapMessage = MqttQueueMessage.wrap(it) { clock.now() }
                messageCount++
                messageQueue.add(wrapMessage)
            }
        }
    }

    override suspend fun awaitUntilEmpty(timeout: Duration): Boolean {
        return withTimeoutOrNull(timeout) {
            do {
                val isEmpty = mutex.withLock {
                    messageCount == 0
                }
                if (isEmpty) {
                    break
                }
                delay(pollInterval)
            } while (true)
        } != null
    }
}

/**
 * キューイングMQTTメッセージ.
 *
 * @property timestamp 送信タイムスタンプ(ms)
 */
@VisibleForTesting
internal class MqttQueueMessage private constructor(
    private val delegated: MqttMessage,
    val timestamp: Instant,
) : MqttMessage by delegated {
    companion object {
        inline fun wrap(message: MqttMessage, block: () -> Instant): MqttQueueMessage {
            return if (message is MqttQueueMessage) {
                message
            } else MqttQueueMessage(message, block())
        }
    }
}
