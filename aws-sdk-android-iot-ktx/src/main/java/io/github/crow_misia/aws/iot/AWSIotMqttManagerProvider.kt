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
@file:Suppress("unused")

package io.github.crow_misia.aws.iot

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager.ClientId
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager.Endpoint
import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.model.ThingName
import java.util.concurrent.ConcurrentHashMap

interface AWSIotMqttManagerProvider {
    fun provide(clientId: ClientId): AWSIotMqttManager

    fun provide(thingName: ThingName) = provide(ClientId.fromString(thingName.name))

    fun allDisconnect()

    companion object {
        private val defaultConfiguration: AWSIotMqttManager.() -> Unit = {
            setMetricsIsEnabled(false)
            isAutoReconnect = false
        }

        @JvmStatic
        @JvmOverloads
        fun create(
            region: Region,
            endpoint: Endpoint,
            configure: AWSIotMqttManager.() -> Unit = defaultConfiguration,
        ): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl(configure) { clientId ->
                AWSIotMqttManager.from(region, clientId, endpoint)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun create(
            endpoint: Endpoint,
            configure: AWSIotMqttManager.() -> Unit = defaultConfiguration,
        ): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl(configure) { clientId ->
                AWSIotMqttManager(clientId.value, endpoint.value)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun create(
            region: Region,
            accountEndpointPrefix: String,
            configure: AWSIotMqttManager.() -> Unit = defaultConfiguration,
        ): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl(configure) { clientId ->
                AWSIotMqttManager(clientId.value, region, accountEndpointPrefix)
            }
        }
    }
}

class AWSIotMqttManagerProviderImpl(
    private val configure: AWSIotMqttManager.() -> Unit,
    private val generator: (clientId: ClientId) -> AWSIotMqttManager,
) : AWSIotMqttManagerProvider {
    private val cache = ConcurrentHashMap<String, AWSIotMqttManager>()

    override fun provide(clientId: ClientId): AWSIotMqttManager {
        return cache.getOrPut(clientId.value) {
            generator(clientId).also {
                it.maxAutoReconnectAttempts = -1
            }
        }.apply(configure)
    }

    override fun allDisconnect() {
        cache.values.removeAll {
            runCatching {
                it.disconnect()
            }.getOrDefault(true)
        }
    }
}
