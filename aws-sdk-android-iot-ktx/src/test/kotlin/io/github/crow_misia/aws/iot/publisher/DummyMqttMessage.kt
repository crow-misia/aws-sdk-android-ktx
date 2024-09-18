package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import java.nio.ByteBuffer

internal data class DummyMqttMessage(
    val count: Int,
    override val retainMode: RetainMode = RetainMode.NONE,
) : MqttMessage {
    override val topicName = TopicName("topic")
    override val userData = null
    override val qos = AWSIotMqttQos.QOS0
    override val data: ByteArray = ByteBuffer.allocate(4).putInt(count).array()
}
