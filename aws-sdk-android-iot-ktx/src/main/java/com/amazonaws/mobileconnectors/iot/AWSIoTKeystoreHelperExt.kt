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
package com.amazonaws.mobileconnectors.iot

import com.amazonaws.AmazonClientException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

@Suppress("unused")
object AWSIoTKeystoreHelperExt {
    private const val AWS_IOT_PEM_BEGIN_CERT_TAG = "-----BEGIN CERTIFICATE-----"
    private const val AWS_IOT_PEM_END_CERT_TAG = "-----END CERTIFICATE-----"

    fun loadX509(certPem: String): Array<X509Certificate> {
        val certBytes = AWSIotKeystoreHelper.parseDERFromPEM(certPem, AWS_IOT_PEM_BEGIN_CERT_TAG, AWS_IOT_PEM_END_CERT_TAG)
        return certBytes.map {
            AWSIotKeystoreHelper.generateCertificateFromDER(it)
        }.toTypedArray()
    }

    fun parsePrivateKeyFromPem(pem: String): PrivateKey {
        val privateKeyReader = PrivateKeyReader(pem)
        return runCatching {
            privateKeyReader.privateKey
        }.getOrElse {
            throw AmazonClientException("An error occurred saving the certificate and key.", it)
        }
    }

    @JvmOverloads
    fun loadKeyStore(
        certId: String,
        certPem: String,
        keyPem: String,
        keystorePassword: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
    ): KeyStore {
        return loadKeyStore(certId, certPem, keyPem, keystorePassword.toCharArray())
    }

    fun loadKeyStore(
        certId: String,
        certPem: String,
        keyPem: String,
        keystorePassword: CharArray,
    ): KeyStore {
        val privateKey = PrivateKeyReader(keyPem).privateKey
        val certificates = loadX509(certPem)
        return loadKeyStore(certId, certificates, privateKey, keystorePassword)
    }

    fun loadKeyStore(
        certId: String,
        certPem: String,
        privateKey: PrivateKey,
        keystorePassword: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
    ): KeyStore {
        val certificates = loadX509(certPem)
        return loadKeyStore(certId, certificates, privateKey, keystorePassword)
    }

    @JvmOverloads
    fun loadKeyStore(
        certId: String,
        certificates: Array<X509Certificate>,
        privateKey: PrivateKey,
        keystorePassword: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
    ): KeyStore {
        return loadKeyStore(certId, certificates, privateKey, keystorePassword.toCharArray())
    }

    fun loadKeyStore(
        certId: String,
        certificates: Array<X509Certificate>,
        privateKey: PrivateKey,
        keystorePassword: CharArray,
    ): KeyStore {
        return runCatching {
            KeyStore.getInstance(KeyStore.getDefaultType()).also {
                it.load(null)
                it.setCertificateEntry(certId, certificates[0])
                it.setKeyEntry(certId, privateKey, keystorePassword, certificates)
            }
        }.getOrElse {
            throw AmazonClientException("Error retrieving certificate and key.", it)
        }
    }

    fun createTempKeystore(
        certId: String,
        keyStore: KeyStore,
        keyStorePassword: String,
    ): KeyStore {
        return createTempKeystore(certId, keyStore, keyStorePassword.toCharArray())
    }

    fun createTempKeystore(
        certId: String,
        keyStore: KeyStore,
        keyStorePassword: CharArray,
    ): KeyStore {
        return runCatching {
            val certs = keyStore.getCertificateChain(certId).copyOf()
            val key = keyStore.getKey(certId, keyStorePassword)
            KeyStore.getInstance(KeyStore.getDefaultType()).also {
                it.load(null)
                it.setCertificateEntry(certId, certs[0])
                it.setKeyEntry(certId, key, AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD.toCharArray(), certs)
            }
        }.getOrElse {
            throw AWSIotCertificateException("Error retrieving certificate and key.", it)
        }
    }
}
