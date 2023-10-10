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

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager

/**
 * Abstract Provisioning Manager.
 *
 * @property mqttManagerProvider AWS IoT Core MQTT Manager Provider
 * @property provisionerProvider AWS IoT Core Fleet Provisioner Provider
 * @property clientIdProvider Client ID Provider for Provisioning
 */
abstract class AbstractProvisioningManager(
    private val mqttManagerProvider: AWSIotMqttManagerProvider,
    private val provisionerProvider: AWSIoTFleetProvisionerProvider,
    private val clientIdProvider: ClientIdProvider,
) : ProvisioningManager {
    /**
     * Provisioning.
     *
     * @param provisioner Fleet Provisioner
     * @param parameters Provisioning Parameters
     * @return Provisioning Response
     */
    protected abstract suspend fun provisioningThing(
        provisioner: AWSIoTFleetProvisioner,
        parameters: Map<String, String>,
    ): AWSIoTProvisioningResponse

    /**
     * Generate Parameters for Provisioning.
     *
     * @param base Base Parameters
     * @return Parameters for Provisioning
     */
    protected open fun generateParameters(base: Map<String, String>): Map<String, String> = base

    /**
     * create MQTT Manager.
     */
    private fun createMqttManager(): AWSIotMqttManager {
        val clientId = clientIdProvider.provide()
        return mqttManagerProvider.provide(clientId)
    }

    override suspend fun provisioning(
        parameters: Map<String, String>,
    ): AWSIoTProvisioningResponse {
        val manager = createMqttManager()
        val provisioner = provisionerProvider.provide(manager)
        return provisioningThing(provisioner, generateParameters(parameters))
    }
}
