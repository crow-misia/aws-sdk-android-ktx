package io.github.crow_misia.aws_sdk_android_iot_ktx

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.security.KeyStore

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingUsingALPN(
    keyStore: KeyStore,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThingUsingALPN(keyStore, templateName, JSONObject(parameters))
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingUsingALPN(
    keyStore: KeyStore,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(templateName, parameters) { connectUsingALPN(keyStore) }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThingWithProxy(
        keyStore,
        proxyHost,
        proxyPort,
        templateName,
        JSONObject(parameters)
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(templateName, parameters) {
        connectWithProxy(
            keyStore,
            proxyHost,
            proxyPort
        )
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    keyStore: KeyStore,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(keyStore, templateName, JSONObject(parameters))
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    keyStore: KeyStore,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(templateName, parameters) { connect(keyStore) }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    credentialsProvider: AWSCredentialsProvider,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(credentialsProvider, templateName, JSONObject(parameters))
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    credentialsProvider: AWSCredentialsProvider,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(templateName, parameters) { connect(credentialsProvider) }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(
        tokenKeyName = tokenKeyName,
        token = token,
        tokenSignature = tokenSignature,
        customAuthorizer = customAuthorizer,
        templateName = templateName,
        parameters = JSONObject(parameters),
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) { connect(tokenKeyName, token, tokenSignature, customAuthorizer) }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    username: String,
    password: String,
    templateName: String,
    parameters: Map<String, String>,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(
        username = username,
        password = password,
        templateName = templateName,
        parameters = JSONObject(parameters),
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    username: String,
    password: String,
    templateName: String,
    parameters: JSONObject,
): Flow<AWSIoTProvisioningResponse> {
    return provisioningThing(templateName, parameters) { connect(username, password) }
}

@ExperimentalCoroutinesApi
@FlowPreview
private suspend inline fun AWSIotMqttManager.provisioningThing(
    templateName: String,
    parameters: JSONObject,
    connect: () -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>,
): Flow<AWSIoTProvisioningResponse> {
    return connect()
        // 接続まで待機
        .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
        .take(1)
        .flatMapConcat {
            publishWithReply("{}", "\$aws/certificates/create/json", AWSIotMqttQos.QOS1)
        }
        .map { JSONObject(String(it)) }
        .flatMapConcat { response ->
            val certificateOwnershipToken = response.getString("certificateOwnershipToken")
            val request = JSONObject().apply {
                put("certificateOwnershipToken", certificateOwnershipToken)
                put("parameters", parameters)
            }
            publishWithReply(
                str = request.toString(),
                topic = "\$aws/provisioning-templates/$templateName/provision/json",
                qos = AWSIotMqttQos.QOS1
            ).map { JSONObject(String(it)) }.map {
                AWSIoTProvisioningResponse(
                    deviceConfiguration = it.getJSONObject("deviceConfiguration"),
                    thingName = it.getString("thingName"),
                    certificateId = response.getString("certificateId"),
                    certificatePem = response.getString("certificatePem"),
                    privateKey = response.getString("privateKey"),
                )
            }
        }
        .onCompletion { disconnect() }
}

/**
 * Things Provisioning Response.
 */
data class AWSIoTProvisioningResponse(
    val deviceConfiguration: JSONObject,
    val thingName: String,
    val certificateId: String,
    val certificatePem: String,
    val privateKey: String,
) {
    fun saveCertificateAndPrivateKey(
        keystorePath: String,
        keystoreName: String,
        keystorePassword: String,
    ) {
        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
            certificateId,
            certificatePem,
            privateKey,
            keystorePath,
            keystoreName,
            keystorePassword
        )
    }
}
