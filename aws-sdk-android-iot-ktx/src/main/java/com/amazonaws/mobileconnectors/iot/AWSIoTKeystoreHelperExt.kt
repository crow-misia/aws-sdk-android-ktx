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
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException

object AWSIoTKeystoreHelperExt {
    private const val AWS_IOT_PEM_BEGIN_CERT_TAG = "-----BEGIN CERTIFICATE-----"
    private const val AWS_IOT_PEM_END_CERT_TAG = "-----END CERTIFICATE-----"

    private fun loadX509(certPem: String): Array<X509Certificate> {
        val certBytes = AWSIotKeystoreHelper.parseDERFromPEM(certPem, AWS_IOT_PEM_BEGIN_CERT_TAG, AWS_IOT_PEM_END_CERT_TAG)
        return certBytes.map {
            AWSIotKeystoreHelper.generateCertificateFromDER(it)
        }.toTypedArray()
    }

    @JvmOverloads
    fun loadKeyStore(
        certId: String,
        certPem: String,
        keyPem: String,
        keystorePassword: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
    ): KeyStore {
        val privateKeyReader = PrivateKeyReader(keyPem)
        val privateKey = try {
            privateKeyReader.privateKey
        } catch (e: IOException) {
            throw AmazonClientException("An error occurred saving the certificate and key.", e)
        } catch (e: InvalidKeySpecException) {
            throw AWSIotCertificateException("An error occurred saving the certificate and key.", e)
        }

        val certs = loadX509(certPem)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        keyStore.setCertificateEntry(certId, certs[0])
        keyStore.setKeyEntry(certId, privateKey, keystorePassword.toCharArray(), certs)
        return keyStore
    }

    fun createTempKeystore(
        certId: String,
        keyStore: KeyStore,
        keyStorePassword: String,
    ): KeyStore {
        return try {
            val certs = keyStore.getCertificateChain(certId).copyOf()
            val key = keyStore.getKey(certId, keyStorePassword.toCharArray())
            KeyStore.getInstance(KeyStore.getDefaultType()).also {
                it.load(null)
                it.setCertificateEntry("cert-alias", certs[0])
                it.setKeyEntry("key-alias", key, AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD.toCharArray(), certs)
            }
        } catch (e: CertificateException) {
            throw AWSIotCertificateException("Error retrieving certificate and key.", e)
        } catch (e: KeyStoreException) {
            throw AWSIotCertificateException("Error retrieving certificate and key.", e)
        } catch (e: UnrecoverableKeyException) {
            throw AWSIotCertificateException("Error retrieving certificate and key.", e)
        } catch (e: NoSuchAlgorithmException) {
            throw AWSIotCertificateException("Error retrieving certificate and key.", e)
        } catch (e: IOException) {
            throw AmazonClientException("Error retrieving certificate and key.", e)
        }
    }
}
