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
import io.github.crow_misia.aws.iot.AWSIoTProvisioningResponse
import io.github.crow_misia.aws.iot.publishWithReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Create Private Keys and Certificate Fleet Provisioner.
 */
@Suppress("unused")
class CreateKeysAndCertificateFleetProvisioner(
    private val mqttManager: AWSIotMqttManager,
) : AWSIoTFleetProvisioner {
    override suspend fun provisioningThing(
        templateName: String,
        parameters: JSONObject,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>
    ): AWSIoTProvisioningResponse {
        return connect(mqttManager)
            // Wait until connected.
            .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
            .map {
                val response = mqttManager.publishWithReply(
                    str = "{}",
                    topic = "\$aws/certificates/create/json",
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
                    privateKeyPem = response.getString("privateKey"),
                )
            }
            .first()
    }
}
