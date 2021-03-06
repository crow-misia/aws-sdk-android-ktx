package io.github.crow_misia.aws.iot

import com.amazonaws.AmazonClientException

sealed class AWSIoTMqttException : AmazonClientException {
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

class AWSIoTMqttDeliveryException(
    message: String,
    val userData: Any?,
) : AWSIoTMqttException(message)

class AWSIoTMqttPublishWithReplyException(
    message: String,
    topic: String,
    response: ByteArray,
    val userData: Any?,
) : AWSIoTMqttException("$message:\ntopic:$topic\nResponse:${String(response)}")
