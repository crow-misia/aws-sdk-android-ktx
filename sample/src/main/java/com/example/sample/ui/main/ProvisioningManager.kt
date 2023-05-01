package com.example.sample.ui.main

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.core.KeyStoreProvider
import io.github.crow_misia.aws.iot.AWSIotMqttManagerProvider
import io.github.crow_misia.aws.iot.keystore.AbstractKeyStoreProvisioningManager
import java.util.UUID

class ProvisioningManager(
    provider: AWSIotMqttManagerProvider,
    templateName: String,
    keyStoreProvider: KeyStoreProvider,
) : AbstractKeyStoreProvisioningManager(provider, templateName, keyStoreProvider) {
    override fun generateTemporaryClientID(): AWSIotMqttManager.ClientId {
        return AWSIotMqttManager.ClientId.fromString(UUID.randomUUID().toString())
    }
}
