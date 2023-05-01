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
@file:Suppress("unused")

package io.github.crow_misia.aws.iot

import com.amazonaws.ClientConfiguration
import com.amazonaws.assumeRoleWithCredentials
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.http.HttpClient
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import io.github.crow_misia.aws.core.Okhttp3HttpClient
import io.github.crow_misia.aws.core.asBasicSessionCredentials
import io.github.crow_misia.aws.core.createNewClient
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.X509Certificate

open class AWSIoTSTSAssumeRoleSessionCredentialsProvider private constructor(
    private var thingName: String,
    private val securityTokenService: AWSSecurityTokenService,
) : AWSCredentialsProvider {
    companion object {
        /** Time before expiry within which credentials will be renewed. */
        const val EXPIRY_TIME_MILLIS = 60 * 1000
    }

    /**
     * The current session credentials.
     */
    private var sessionCredentials: AWSSessionCredentials? = null

    /**
     * The expiration time for the current session credentials.
     */
    private var sessionCredentialsExpiration: Long? = null

    @JvmOverloads
    constructor(
        thingName: String,
        keyStore: KeyStore,
        password: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
        rootCa: X509Certificate,
        stsEndpoint: String,
        roleAliasName: String,
        okHttpClient: OkHttpClient,
        clientConfiguration: ClientConfiguration,
    ): this(
        thingName = thingName,
        client = Okhttp3HttpClient(okHttpClient.createNewClient(
            config = clientConfiguration,
            keyStore = keyStore,
            password = password,
            caPublicKeyProvider = { rootCa },
        )),
        stsEndpoint = stsEndpoint,
        roleAliasName = roleAliasName,
        clientConfiguration = clientConfiguration,
    )

    constructor(
        thingName: String,
        client: HttpClient,
        stsEndpoint: String,
        roleAliasName: String,
        clientConfiguration: ClientConfiguration,
    ): this(
        thingName = thingName,
        securityTokenService = AWSSecurityTokenServiceClient(
            StaticCredentialsProvider(AnonymousAWSCredentials()),
            clientConfiguration,
            client,
        ),
        stsEndpoint = stsEndpoint,
        roleAliasName = roleAliasName,
    )

    constructor(
        thingName: String,
        securityTokenService: AWSSecurityTokenService,
        stsEndpoint: String,
        roleAliasName: String,
    ): this(
        thingName = thingName,
        securityTokenService = securityTokenService.also {
            it.setEndpoint("$stsEndpoint/role-aliases/$roleAliasName/")
        }
    )

    fun setThingName(thingName: String) {
        this.thingName = thingName
        sessionCredentials = null
    }

    fun setSTSClientEndpoint(endpoint: String) {
        securityTokenService.setEndpoint(endpoint)
        sessionCredentials = null
    }

    override fun getCredentials(): AWSCredentials {
        if (neededNewSession()) {
            startSession()
        }
        return checkNotNull(sessionCredentials)
    }

    override fun refresh() {
        startSession()
    }

    private fun startSession() {
        val assumeRoleResult = securityTokenService.assumeRoleWithCredentials(
            AssumeRoleWithCredentialsRequest(thingName)
        )
        val stsCredentials = assumeRoleResult.credentials
        sessionCredentials = stsCredentials?.asBasicSessionCredentials()
        sessionCredentialsExpiration = (stsCredentials?.expiration?.time ?: 0L) + System.currentTimeMillis()
    }

    private fun neededNewSession(): Boolean {
        sessionCredentials ?: return true
        val expiration = sessionCredentialsExpiration ?: return true

        val timeRemaining = expiration - System.currentTimeMillis()
        return timeRemaining < EXPIRY_TIME_MILLIS
    }
}
