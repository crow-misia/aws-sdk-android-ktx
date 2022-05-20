package io.github.crow_misia.aws.iot

data class SubscribeData(
    val topic: String,
    val data: ByteArray,
)
