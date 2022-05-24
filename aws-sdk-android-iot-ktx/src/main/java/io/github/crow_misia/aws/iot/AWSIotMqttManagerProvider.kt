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
