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
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager.ClientId
import io.github.crow_misia.aws.core.KeyStoreProvider
import io.github.crow_misia.aws.iot.AWSIotMqttManagerProvider
import io.github.crow_misia.aws.iot.provisioningThing
import org.json.JSONObject
import java.util.UUID

/**
 * Abstract Provisioning Manager.
 *
 * @property provider AWS IoT Core MQTT Manager Provider
 */
abstract class AbstractProvisioningManager(
    private val provider: AWSIotMqttManagerProvider,
) {
    /**
     * Provisioning.
     *
     * @param manager MQTT Manager
     * @param parameters Provisioning Parameters
     * @return Provisioning Response
     */
    protected abstract suspend fun provisioningThing(manager: AWSIotMqttManager, parameters: JSONObject): AWSIoTProvisioningResponse

    /**
     * Generate Temporary ClientID
     *
     * @return ClientID
     */
    protected abstract fun generateTemporaryClientID(): ClientId

    /**
     * Generate Parameters for Provisioning.
     *
     * @param base Base Parameters
     * @return Parameters for Provisioning
     */
    open fun generateParameters(base: JSONObject): JSONObject = base

    /**
     * create MQTT Manager.
     */
    private fun createMqttManager(): AWSIotMqttManager {
        // generate temporary ClientID
        val clientId = generateTemporaryClientID()
        return provider.provide(clientId)
    }

    /**
     * doing provisioning.
     *
     * @param serialNumber Device unique ID
     */
    suspend fun provisioning(serialNumber: String): AWSIoTProvisioningResponse {
        val manager = createMqttManager()
        val parameters = generateParameters(JSONObject().put("SerialNumber", serialNumber))
        return provisioningThing(manager, parameters)
    }
}
