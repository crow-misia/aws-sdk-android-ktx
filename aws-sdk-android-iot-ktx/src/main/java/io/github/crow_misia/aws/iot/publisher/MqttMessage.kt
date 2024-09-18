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

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos

/**
 * MQTT送信メッセージ.
 */
interface MqttMessage {
    /**
     * トピック名.
     */
    val topicName: TopicName

    /**
     * 送信データ.
     */
    val data: ByteArray

    /**
     * QoS.
     */
    val qos: AWSIotMqttQos

    /**
     * ユーザデータ.
     */
    val userData: Any?

    /**
     * メッセージを永続化するか.
     */
    val retainMode: RetainMode
}

enum class RetainMode(val isRetained: Boolean) {
    /** 永続化しない */
    NONE(false),
    /** 未送信データを上書き */
    OVERWRITE(true),
    /** 常に永続化 */
    ALWAYS(true),
}
