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
import org.json.JSONObject

/**
 * Abstract Provisioning Manager.
 *
 * @property provider AWS IoT Core MQTT Manager Provider
 * @property clientIdProvider Client ID Provider for Provisioning
 */
abstract class AbstractProvisioningManager(
    private val provider: AWSIotMqttManagerProvider,
    private val clientIdProvider: ClientIdProvider,
) : ProvisioningManager {
    /**
     * Provisioning.
     *
     * @param manager MQTT Manager
     * @param parameters Provisioning Parameters
     * @return Provisioning Response
     */
    protected abstract suspend fun provisioningThing(manager: AWSIotMqttManager, parameters: JSONObject): AWSIoTProvisioningResponse

    /**
     * Generate Parameters for Provisioning.
     *
     * @param base Base Parameters
     * @return Parameters for Provisioning
     */
    protected open fun generateParameters(base: JSONObject): JSONObject = base

    /**
     * create MQTT Manager.
     */
    private fun createMqttManager(): AWSIotMqttManager {
        val clientId = clientIdProvider.provide()
        return provider.provide(clientId)
    }

    override suspend fun provisioning(parameters: JSONObject): AWSIoTProvisioningResponse {
        val manager = createMqttManager()
        return provisioningThing(manager, generateParameters(parameters))
    }
}
