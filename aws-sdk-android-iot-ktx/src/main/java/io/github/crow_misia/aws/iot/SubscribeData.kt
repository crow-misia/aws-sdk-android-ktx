package io.github.crow_misia.aws.iot

data class SubscribeData(
    val topic: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SubscribeData

        if (topic != other.topic) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
