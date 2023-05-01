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
package io.github.crow_misia.aws.core

import com.amazonaws.ClientConfiguration
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun OkHttpClient.createNewClient(
    config: ClientConfiguration,
    keyStore: KeyStore,
    password: String?,
    caPublicKeyProvider: () -> X509Certificate,
): OkHttpClient {
    return newBuilder().also {
        // configure the connection
        it.connectTimeout(config.connectionTimeout.toLong(), TimeUnit.MILLISECONDS)
        it.readTimeout(config.socketTimeout.toLong(), TimeUnit.MILLISECONDS)
        // disable redirect and cache
        it.cache(null)
        it.followRedirects(false)
        it.followSslRedirects(false)

        // client certificate
        it.sslSocketFactory(keyStore, password, caPublicKeyProvider())
    }.build()
}

private fun OkHttpClient.Builder.sslSocketFactory(
    keyStore: KeyStore,
    password: String?,
    caPublicKey: X509Certificate,
) {
    val trustedStore = KeyStore.getInstance(KeyStore.getDefaultType())
    trustedStore.load(null)
    trustedStore.setCertificateEntry(caPublicKey.subjectX500Principal.name, caPublicKey)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(trustedStore)

    val trustManagers = trustManagerFactory.trustManagers
    check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
        "Unexpected default trust managers:" + Arrays.toString(trustManagers)
    }
    val trustManager = trustManagers[0] as X509TrustManager

    val keyManagerFactory = KeyManagerFactory.getInstance("X509")
    keyManagerFactory.init(keyStore, password?.toCharArray())

    val sc = SSLContext.getInstance("TLS")
    sc.init(keyManagerFactory.keyManagers, arrayOf(trustManager), null)
    sslSocketFactory(sc.socketFactory, trustManager)
}
