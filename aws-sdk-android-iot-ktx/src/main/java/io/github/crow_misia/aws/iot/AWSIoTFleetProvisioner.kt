/**
 * Copyright (C) 2023 Zenichi Amano.
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
package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIoTKeystoreHelperExt
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.security.KeyStore
import java.security.PrivateKey

@Suppress("unused")
interface AWSIoTFleetProvisioner {
    suspend fun provisioningThingUsingALPN(
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

    suspend fun provisioningThingUsingALPN(
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

    suspend fun provisioningThingWithProxy(
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

    suspend fun provisioningThingWithProxy(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    suspend fun provisioningThing(
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

    /**
     * Provisioning.
     *
     * @param templateName
     */
    suspend fun provisioningThing(
        templateName: String,
        parameters: JSONObject,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>,
    ): AWSIoTProvisioningResponse
}

/**
 * Things Provisioning Response.
 */
data class AWSIoTProvisioningResponse(
    val deviceConfiguration: JSONObject,
    val thingName: String,
    val certificateId: String,
    val certificatePem: String,
    val privateKey: PrivateKey,
) {
    constructor(
        deviceConfiguration: JSONObject,
        thingName: String,
        certificateId: String,
        certificatePem: String,
        privateKeyPem: String,
    ) : this(
        deviceConfiguration = deviceConfiguration,
        thingName = thingName,
        certificateId = certificateId,
        certificatePem = certificatePem,
        privateKey = AWSIoTKeystoreHelperExt.parsePrivateKeyFromPem(privateKeyPem),
    )

    /**
     * Save Certificate and Private Key.
     */
    @JvmOverloads
    fun saveCertificateAndPrivateKey(
        keystorePath: String,
        keystoreName: String,
        keystorePassword: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
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
