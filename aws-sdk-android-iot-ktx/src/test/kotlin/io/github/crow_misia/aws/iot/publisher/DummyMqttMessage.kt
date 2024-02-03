package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos

internal data class DummyMqttMessage(val count: Int) : MqttMessage {
    override val topicName = TopicName("topic")
    override val userData = null
    override val isRetained = false
    override val qos = AWSIotMqttQos.QOS0
    override val data: ByteArray = byteArrayOf(count.toByte())
}