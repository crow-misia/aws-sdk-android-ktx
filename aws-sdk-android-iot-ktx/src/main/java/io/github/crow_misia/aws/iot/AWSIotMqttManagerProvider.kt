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
import com.amazonaws.regions.Region
import java.util.concurrent.ConcurrentHashMap

interface AWSIotMqttManagerProvider {
    fun provide(clientId: AWSIotMqttManager.ClientId): AWSIotMqttManager {
        return provide(clientId = clientId.value)
    }

    fun provide(clientId: String): AWSIotMqttManager

    fun allDisconnect()

    companion object {
        @JvmStatic
        fun create(region: Region, endpoint: AWSIotMqttManager.Endpoint): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl { clientId ->
                AWSIotMqttManager.from(region, AWSIotMqttManager.ClientId.fromString(clientId), endpoint)
            }
        }

        @JvmStatic
        fun create(endpoint: AWSIotMqttManager.Endpoint): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl { clientId ->
                AWSIotMqttManager(clientId, endpoint.value)
            }
        }

        @JvmStatic
        fun create(region: Region, accountEndpointPrefix: String): AWSIotMqttManagerProvider {
            return AWSIotMqttManagerProviderImpl { clientId ->
                AWSIotMqttManager(clientId, region, accountEndpointPrefix)
            }
        }
    }
}

class AWSIotMqttManagerProviderImpl(
    private val generator: (clientId: String) -> AWSIotMqttManager,
) : AWSIotMqttManagerProvider {
    private val cache: MutableMap<String, AWSIotMqttManager> = ConcurrentHashMap()

    override fun provide(clientId: String): AWSIotMqttManager {
        return cache.getOrPut(clientId) {
            generator(clientId)
        }
    }

    override fun allDisconnect() {
        cache.values.removeAll {
            try {
                it.disconnect()
            } catch (_: Throwable) {
                true
            }
        }
    }
}
