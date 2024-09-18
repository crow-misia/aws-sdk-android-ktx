package com.example.sample.ui

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import io.github.crow_misia.aws.iot.publisher.MqttMessage
import io.github.crow_misia.aws.iot.publisher.RetainMode
import io.github.crow_misia.aws.iot.publisher.TopicName

class SampleMqttMessage(time: Long) : MqttMessage {
    override val topicName: TopicName = TopicName("test")

    override val data: ByteArray = "this is sample. $time".toByteArray()

    override val retainMode: RetainMode = RetainMode.NONE

    override val userData: Any? = null

    override val qos: AWSIotMqttQos = AWSIotMqttQos.QOS1
}
