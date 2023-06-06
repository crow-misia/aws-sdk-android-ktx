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
package io.github.crow_misia.aws.iot.provisioning

import android.security.keystore.KeyProperties
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import io.github.crow_misia.aws.iot.AWSIoTFleetProvisioner
import io.github.crow_misia.aws.iot.AWSIoTProvisioningResponse
import io.github.crow_misia.aws.iot.publishWithReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.json.JSONObject
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * Create Certificate from CSR Fleet Provisioner.
 */
@Suppress("unused")
class CreateCertificateFromCSRFleetProvisioner @JvmOverloads constructor(
    private val mqttManager: AWSIotMqttManager,
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.Sha256WithEcdsa,
    private val securityProvider: String? = null,
) : AWSIoTFleetProvisioner {
    override suspend fun provisioningThing(
        templateName: String,
        parameters: JSONObject,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>
    ): AWSIoTProvisioningResponse {
        val certificateRequest = createCSR()

        return connect(mqttManager)
            // Wait until connected.
            .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
            .map {
                val requestCsr = JSONObject().apply {
                    put("certificateSigningRequest", certificateRequest.csr)
                }

                val response = mqttManager.publishWithReply(
                    data = requestCsr,
                    topic = "\$aws/certificates/create-from-csr/json",
                    qos = AWSIotMqttQos.QOS1,
                ).let { (_, data) -> JSONObject(String(data)) }

                val certificateOwnershipToken = response.getString("certificateOwnershipToken")

                val json = mqttManager.publishWithReply(
                    data = JSONObject().apply {
                        put("certificateOwnershipToken", certificateOwnershipToken)
                        put("parameters", parameters)
                    },
                    topic = "\$aws/provisioning-templates/$templateName/provision/json",
                    qos = AWSIotMqttQos.QOS1
                ).let { (_, data) -> JSONObject(String(data)) }

                AWSIoTProvisioningResponse(
                    deviceConfiguration = json.getJSONObject("deviceConfiguration"),
                    thingName = json.getString("thingName"),
                    certificateId = response.getString("certificateId"),
                    certificatePem = response.getString("certificatePem"),
                    privateKey = certificateRequest.privateKey,
                )
            }
            .first()
    }

    private fun createCSR(): CertificateRequestData {
        val keyPairGenerator = securityProvider?.let {
            KeyPairGenerator.getInstance(signingAlgorithm.keyAlgorithm, it)
        } ?: KeyPairGenerator.getInstance(signingAlgorithm.keyAlgorithm)

        keyPairGenerator.initialize(signingAlgorithm.keySpec)

        val pair = keyPairGenerator.generateKeyPair()

        val p10Builder = JcaPKCS10CertificationRequestBuilder(
            X500Principal("CN=AWS IoT Certificate"), pair.public
        )
        val csBuilder = JcaContentSignerBuilder(signingAlgorithm.algorithmName)
        val signer = csBuilder.build(pair.private)
        val csr = p10Builder.build(signer)

        val pemObject = PemObject("CERTIFICATE REQUEST", csr.encoded)
        val csrAsString = pemObject.asString()
        val privateKey = pair.private

        return CertificateRequestData(
            privateKey = privateKey,
            csr = csrAsString,
        )
    }

    private fun PemObject.asString(): String {
        val writer = StringWriter()
        PemWriter(writer).use {
            it.writeObject(this)
        }
        return writer.toString()
    }

    data class CertificateRequestData(
        val privateKey: PrivateKey,
        val csr: String,
    )

    open class SigningAlgorithm(
        val algorithmName: String,
        val keyAlgorithm: String,
        val keySpec: AlgorithmParameterSpec,
    ) {
        companion object {
            private const val RSA_KEY_SIZE = 2048
            private const val EC_KEY_NAME = "secp256r1"
        }

        object Sha256WithRsa : SigningAlgorithm(
            algorithmName = "SHA256withRSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_RSA,
            keySpec = RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4),
        )
        object Sha384WithRsa : SigningAlgorithm(
            algorithmName = "SHA384withRSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_RSA,
            keySpec = RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4),
        )
        object Sha512WithRsa : SigningAlgorithm(
            algorithmName = "SHA512withRSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_RSA,
            keySpec = RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4),
        )
        object Sha256WithEcdsa : SigningAlgorithm(
            algorithmName = "SHA256withECDSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_EC,
            keySpec = ECGenParameterSpec(EC_KEY_NAME),
        )
        object Sha384WithEcdsa : SigningAlgorithm(
            algorithmName = "SHA384withECDSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_EC,
            keySpec = ECGenParameterSpec(EC_KEY_NAME),
        )
        object Sha512WithEcdsa : SigningAlgorithm(
            algorithmName = "SHA512withECDSA",
            keyAlgorithm = KeyProperties.KEY_ALGORITHM_EC,
            keySpec = ECGenParameterSpec(EC_KEY_NAME),
        )
    }
}
