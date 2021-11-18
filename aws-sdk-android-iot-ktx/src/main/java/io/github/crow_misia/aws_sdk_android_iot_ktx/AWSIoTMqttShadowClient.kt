package io.github.crow_misia.aws_sdk_android_iot_ktx

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.KeyStore

class AWSIoTMqttShadowClient(
    private val manager: AWSIotMqttManager,
    private val thingName: String,
) {
    @ExperimentalCoroutinesApi
    suspend fun connectUsingALPN(keyStore: KeyStore): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connectUsingALPN(keyStore)
    }

    @ExperimentalCoroutinesApi
    suspend fun connectWithProxy(keyStore: KeyStore, proxyHost: String, proxyPort: Int): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connectWithProxy(keyStore, proxyHost, proxyPort)
    }

    @ExperimentalCoroutinesApi
    suspend fun connect(keyStore: KeyStore): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connect(keyStore)
    }

    @ExperimentalCoroutinesApi
    suspend fun connect(credentialsProvider: AWSCredentialsProvider): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connect(credentialsProvider)
    }

    @ExperimentalCoroutinesApi
    suspend fun connect(tokenKeyName: String, token: String, tokenSignature: String, customAuthorizer: String): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connect(tokenKeyName, token, tokenSignature, customAuthorizer)
    }

    @ExperimentalCoroutinesApi
    suspend fun connect(username: String, password: String): Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus> {
        return manager.connect(username, password)
    }

    fun disconnect() {
        manager.disconnect()
    }

    @ExperimentalCoroutinesApi
    suspend fun get(shadowName: String? = null): JSONObject {
        return manager.publishWithReply(
            str = "",
            topic = getTopicName(shadowName, "get"),
            qos = AWSIotMqttQos.QOS1,
        ).map { JSONObject(String(it)) }
         .first()
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
        ).map { JSONObject(String(it)) }
         .first()
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
        ).map { JSONObject(String(it)) }
         .first()
    }

    private fun getTopicName(shadowName: String?, method: String): String {
        return shadowName?.let {
            "\$aws/things/${thingName}/shadow/name/$shadowName/$method"
        } ?: "\$aws/things/${thingName}/shadow/$method"
    }
}