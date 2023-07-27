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

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import io.github.crow_misia.aws.iot.AWSIoTFleetProvisioner
import io.github.crow_misia.aws.iot.AWSIoTMqttPublishWithReplyException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningResponse
import io.github.crow_misia.aws.iot.model.CreateKeysAndCertificateResponse
import io.github.crow_misia.aws.iot.model.ProvisioningErrorResponse
import io.github.crow_misia.aws.iot.model.RegisterThingRequest
import io.github.crow_misia.aws.iot.model.RegisterThingResponse
import io.github.crow_misia.aws.iot.publishWithReply
import io.github.crow_misia.aws.iot.publisher.TopicName
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Duration

/**
 * Create Private Keys and Certificate Fleet Provisioner.
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("unused", "UnnecessaryOptInAnnotation")
class CreateKeysAndCertificateFleetProvisioner(
    private val mqttManager: AWSIotMqttManager,
) : AWSIoTFleetProvisioner {
    @OptIn(FlowPreview::class)
    override suspend fun provisioningThing(
        templateName: String,
        parameters: Map<String, String>,
        timeout: Duration,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>
    ): AWSIoTProvisioningResponse {
        return connect(mqttManager)
            // Wait until connected.
            .timeout(timeout)
            .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
            .map {
                val keysResponse = mqttManager.publishWithReply(
                    topic = TopicName("\$aws/certificates/create/cbor"),
                    qos = AWSIotMqttQos.QOS1,
                ).let { Cbor.decodeFromByteArray<CreateKeysAndCertificateResponse>(it.data) }

                val registerRequest = Cbor.encodeToByteArray(RegisterThingRequest(
                    certificateOwnershipToken = keysResponse.certificateOwnershipToken,
                    parameters = parameters,
                ))
                val registerResponse = mqttManager.publishWithReply(
                    data = registerRequest,
                    topic = TopicName("\$aws/provisioning-templates/$templateName/provision/cbor"),
                    qos = AWSIotMqttQos.QOS1,
                ).let { Cbor.decodeFromByteArray<RegisterThingResponse>(it.data) }

                AWSIoTProvisioningResponse(
                    deviceConfiguration = registerResponse.deviceConfiguration,
                    thingName = registerResponse.thingName,
                    certificateId = keysResponse.certificateId,
                    certificatePem = keysResponse.certificatePem,
                    privateKeyPem = keysResponse.privateKey,
                )
            }
            .catch { e ->
                if (e is AWSIoTMqttPublishWithReplyException) {
                    val errorResponse = Cbor.decodeFromByteArray<ProvisioningErrorResponse>(e.response)
                    throw AWSIoTProvisioningException(errorResponse)
                }
                throw e
            }
            .first()
    }
}
