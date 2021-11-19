package io.github.crow_misia.aws_sdk_android_iot_ktx

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class AWSIoTMqttShadowClient internal constructor(
    private val manager: AWSIotMqttManager,
    private val thingName: String,
) {
    fun disconnect() {
        manager.disconnect()
    }

    @ExperimentalCoroutinesApi
    suspend fun get(shadowName: String? = null): JSONObject {
        return manager.publishWithReply(
            str = "",
            topic = getTopicName(shadowName, "get"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }.first()
    }

    @ExperimentalCoroutinesApi
    suspend fun update(reported: JSONObject, shadowName: String? = null): JSONObject {
        val state = JSONObject().apply {
            put("state", JSONObject().apply {
                put("reported", reported)
            })
        }
        return manager.publishWithReply(
            str = state.toString(),
            topic = getTopicName(shadowName, "update"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }.first()
    }

    @ExperimentalCoroutinesApi
    suspend fun subscribeDelta(shadowName: String? = null): Flow<JSONObject> {
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/delta"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }
    }

    @ExperimentalCoroutinesApi
    suspend fun subscribeDocuments(shadowName: String? = null): Flow<JSONObject> {
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/documents"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }
    }

    @ExperimentalCoroutinesApi
    suspend fun delete(shadowName: String? = null) {
        manager.publishWithReply(
            str = "",
            topic = getTopicName(shadowName, "delete"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }.first()
    }

    private fun getTopicName(shadowName: String?, method: String): String {
        return shadowName?.let {
            "\$aws/things/${thingName}/shadow/name/$shadowName/$method"
        } ?: "\$aws/things/${thingName}/shadow/$method"
    }
}

fun AWSIotMqttManager.asShadowClient(thingName: String): AWSIoTMqttShadowClient {
    return AWSIoTMqttShadowClient(this, thingName)
}
