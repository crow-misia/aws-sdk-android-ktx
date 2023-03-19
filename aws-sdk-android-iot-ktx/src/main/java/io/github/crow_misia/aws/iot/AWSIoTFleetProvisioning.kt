/**
 * Copyright (C) 2021 Zenichi Amano.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.KeyStore

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

private suspend inline fun AWSIotMqttManager.provisioningThing(
    templateName: String,
    parameters: JSONObject,
    crossinline connect: suspend () -> Flow<AWSIotMqttClientStatus>,
): AWSIoTProvisioningResponse {
    return connect()
        // Wait until connected.
        .filter { it == AWSIotMqttClientStatus.Connected }
        .map {
            val (_, data) = publishWithReply(str = "{}", topic = "\$aws/certificates/create/json", qos = AWSIotMqttQos.QOS1)
            val response = JSONObject(String(data))
            val certificateOwnershipToken = response.getString("certificateOwnershipToken")
            val request = JSONObject().apply {
                put("certificateOwnershipToken", certificateOwnershipToken)
                put("parameters", parameters)
            }
            val json = publishWithReply(
                str = request.toString(),
                topic = "\$aws/provisioning-templates/$templateName/provision/json",
                qos = AWSIotMqttQos.QOS1
            ).let { (_, data) ->
                JSONObject(String(data))
            }
            AWSIoTProvisioningResponse(
                deviceConfiguration = json.getJSONObject("deviceConfiguration"),
                thingName = json.getString("thingName"),
                certificateId = response.getString("certificateId"),
                certificatePem = response.getString("certificatePem"),
                privateKey = response.getString("privateKey"),
            )
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
