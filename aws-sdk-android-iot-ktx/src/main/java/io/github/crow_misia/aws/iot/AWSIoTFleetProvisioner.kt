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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.security.KeyStore
import java.security.PrivateKey
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface AWSIoTFleetProvisioner {
    companion object {
        // default provisioning timeout duration.
        val defaultTimeout = 30.seconds
    }

    suspend fun provisioningThingUsingALPN(
        keyStore: KeyStore,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connectUsingALPN(keyStore = keyStore)
    }

    suspend fun provisioningThingWithProxy(
        keyStore: KeyStore,
        proxyHost: String,
        proxyPort: Int,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connectWithProxy(keyStore = keyStore, proxyHost = proxyHost, proxyPort = proxyPort)
    }

    suspend fun provisioningThing(
        keyStore: KeyStore,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connect(keyStore = keyStore)
    }

    suspend fun provisioningThing(
        credentialsProvider: AWSCredentialsProvider,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connect(credentialsProvider = credentialsProvider)
    }

    suspend fun provisioningThing(
        tokenKeyName: String,
        token: String,
        tokenSignature: String,
        customAuthorizer: String,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connect(
            tokenKeyName = tokenKeyName,
            token = token,
            tokenSignature = tokenSignature,
            customAuthorizer = customAuthorizer
        )
    }

    suspend fun provisioningThing(
        username: String,
        password: String,
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
    ) = provisioningThing(
        templateName = templateName,
        parameters = parameters,
        timeout = timeout,
        context = context,
    ) {
        connect(
            username = username,
            password = password,
        )
    }

    /**
     * Provisioning.
     *
     * @param templateName
     */
    suspend fun provisioningThing(
        templateName: String,
        parameters: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
        context: CoroutineContext = Dispatchers.IO,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>,
    ): AWSIoTProvisioningResponse
}

/**
 * Things Provisioning Response.
 */
data class AWSIoTProvisioningResponse(
    val deviceConfiguration: Map<String, String>,
    val thingName: String,
    val certificateId: String,
    val certificatePem: String,
    val privateKey: PrivateKey,
) {
    constructor(
        deviceConfiguration: Map<String, String>,
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
