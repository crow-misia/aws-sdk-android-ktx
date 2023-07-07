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

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.http.HttpClient
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import io.github.crow_misia.aws.core.Okhttp3HttpClient
import io.github.crow_misia.aws.core.createNewClient
import okhttp3.OkHttpClient
import java.security.KeyStore

/**
 * STS Client.
 */
@Suppress("unused")
class AWSIoTSecurityTokenServiceClient @JvmOverloads constructor(
    stsEndpoint: String,
    awsCredentialsProvider: AWSCredentialsProvider = StaticCredentialsProvider(AnonymousAWSCredentials()),
    clientConfiguration: ClientConfiguration = ClientConfiguration(),
    httpClient: HttpClient,
) : AWSSecurityTokenServiceClient(awsCredentialsProvider, clientConfiguration, httpClient) {
    @JvmOverloads
    constructor(
        stsEndpoint: String,
        okHttpClient: OkHttpClient,
        clientConfiguration: ClientConfiguration = ClientConfiguration(),
    ): this(
        stsEndpoint = stsEndpoint,
        clientConfiguration = clientConfiguration,
        httpClient = Okhttp3HttpClient(okHttpClient, clientConfiguration),
    )

    @JvmOverloads
    constructor(
        keyStore: KeyStore,
        password: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
        caCertificatesProvider: X509CertificatesProvider,
        stsEndpoint: String,
        okHttpClient: OkHttpClient,
        clientConfiguration: ClientConfiguration = ClientConfiguration(),
    ): this(
        keyStore = keyStore,
        password = password.toCharArray(),
        caCertificatesProvider = caCertificatesProvider,
        stsEndpoint = stsEndpoint,
        okHttpClient = okHttpClient,
        clientConfiguration = clientConfiguration,
    )

    @JvmOverloads
    constructor(
        keyStore: KeyStore,
        password: CharArray,
        caCertificatesProvider: X509CertificatesProvider,
        stsEndpoint: String,
        okHttpClient: OkHttpClient,
        clientConfiguration: ClientConfiguration = ClientConfiguration(),
    ): this(
        stsEndpoint = stsEndpoint,
        clientConfiguration = clientConfiguration,
        okHttpClient = okHttpClient.createNewClient(
            keyStore = keyStore,
            password = password,
            caCertificates = caCertificatesProvider.provide(),
        ),
    )

    init {
        setEndpoint(stsEndpoint)
    }
}
