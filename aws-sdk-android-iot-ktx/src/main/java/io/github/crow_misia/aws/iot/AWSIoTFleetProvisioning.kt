package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
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
): AWSIoTProvisioningResponse {
    return provisioningThingUsingALPN(
        keyStore = keyStore,
        templateName = templateName,
        parameters = JSONObject(parameters),
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingUsingALPN(
    keyStore: KeyStore,
    templateName: String,
    parameters: JSONObject,
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connectUsingALPN(keyStore = keyStore)
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThingWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
    templateName: String,
    parameters: Map<String, String>,
): AWSIoTProvisioningResponse {
    return provisioningThingWithProxy(
        keyStore = keyStore,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        templateName = templateName,
        parameters = JSONObject(parameters)
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
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connectWithProxy(keyStore = keyStore, proxyHost = proxyHost, proxyPort = proxyPort)
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    keyStore: KeyStore,
    templateName: String,
    parameters: Map<String, String>,
): AWSIoTProvisioningResponse {
    return provisioningThing(
        keyStore = keyStore,
        templateName = templateName,
        parameters = JSONObject(parameters),
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    keyStore: KeyStore,
    templateName: String,
    parameters: JSONObject,
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connect(keyStore = keyStore)
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    credentialsProvider: AWSCredentialsProvider,
    templateName: String,
    parameters: Map<String, String>,
): AWSIoTProvisioningResponse {
    return provisioningThing(
        credentialsProvider = credentialsProvider,
        templateName = templateName,
        parameters = JSONObject(parameters),
    )
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    credentialsProvider: AWSCredentialsProvider,
    templateName: String,
    parameters: JSONObject,
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connect(credentialsProvider = credentialsProvider)
    }
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
): AWSIoTProvisioningResponse {
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
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connect(
            tokenKeyName = tokenKeyName,
            token = token,
            tokenSignature = tokenSignature,
            customAuthorizer = customAuthorizer
        )
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
suspend fun AWSIotMqttManager.provisioningThing(
    username: String,
    password: String,
    templateName: String,
    parameters: Map<String, String>,
): AWSIoTProvisioningResponse {
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
): AWSIoTProvisioningResponse {
    return provisioningThing(
        templateName = templateName,
        parameters = parameters,
    ) {
        connect(
            username = username,
            password = password,
        )
    }
}

@ExperimentalCoroutinesApi
@FlowPreview
private suspend inline fun AWSIotMqttManager.provisioningThing(
    templateName: String,
    parameters: JSONObject,
    crossinline connect: suspend () -> Flow<AWSIotMqttClientStatus>,
): AWSIoTProvisioningResponse {
    return connect()
        // Wait until connected.
        .filter { it == AWSIotMqttClientStatus.Connected }
        .flatMapConcat {
            publishWithReply(str = "{}", topic = "\$aws/certificates/create/json", qos = AWSIotMqttQos.QOS1)
        }
        .map { (_, data) -> JSONObject(String(data)) }
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
            ).map { (_, data) ->
                JSONObject(String(data))
            }.map {
                AWSIoTProvisioningResponse(
                    deviceConfiguration = it.getJSONObject("deviceConfiguration"),
                    thingName = it.getString("thingName"),
                    certificateId = response.getString("certificateId"),
                    certificatePem = response.getString("certificatePem"),
                    privateKey = response.getString("privateKey"),
                )
            }
        }
        .first()
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
