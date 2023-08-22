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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * MQTT送信メッセージキュー.
 */
interface MqttMessageQueue {
    /**
     * Flowを取得.
     */
    fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage>

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
        fun createMessageQueue(): MqttMessageQueue {
            return ChannelMqttMessageQueue()
        }

        fun wrap(block: suspend FlowCollector<MqttMessage>.() -> Unit): MqttMessageQueue {
            return FlowMqttMessageQueue(flow(block))
        }
    }
}

internal class FlowMqttMessageQueue(
    private val parentFlow: Flow<MqttMessage>,
) : MqttMessageQueue {
    override fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        return parentFlow
            .onEach {
                client.publish(it.data, it.topicName, it.qos, it.userData, it.isRetained)
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
    capacity: Int = Channel.UNLIMITED,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_LATEST,
) : MqttMessageQueue {
    private val queue = Channel<MqttMessage>(capacity = capacity, onBufferOverflow = onBufferOverflow)

    override fun asFlow(client: AWSIotMqttManager): Flow<MqttMessage> {
        return queue.receiveAsFlow()
            .onEach {
                client.publish(it.data, it.topicName, it.qos, it.userData, it.isRetained)
            }
    }

    override suspend fun send(message: MqttMessage) {
        queue.send(message)
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
}