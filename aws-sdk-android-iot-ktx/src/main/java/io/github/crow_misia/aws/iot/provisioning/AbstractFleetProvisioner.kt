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
import io.github.crow_misia.aws.iot.AWSIoTFleetProvisioner
import io.github.crow_misia.aws.iot.AWSIoTMqttPublishWithReplyException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningResponse
import io.github.crow_misia.aws.iot.model.ProvisioningErrorResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Abstract Fleet Provisioner.
 */
abstract class AbstractFleetProvisioner(
    protected val mqttManager: AWSIotMqttManager,
) : AWSIoTFleetProvisioner {
    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun provisioningThing(
        templateName: String,
        parameters: Map<String, String>,
        timeout: Duration,
        context: CoroutineContext,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>,
    ): AWSIoTProvisioningResponse = withContext(context) {
        withTimeout(timeout) {
            connect(mqttManager)
                // Wait until connected.
                .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
                .take(1)
                .map { process(templateName, parameters) }
                .catch { handleError(it) }
                .first()
        }
    }

    @ExperimentalSerializationApi
    private fun handleError(cause: Throwable) {
        when (cause) {
            is AWSIoTMqttPublishWithReplyException -> {
                val errorResponse = Cbor.decodeFromByteArray<ProvisioningErrorResponse>(cause.response)
                throw AWSIoTProvisioningException(errorResponse)
            }
            else -> throw cause
        }
    }

    abstract suspend fun process(
        templateName: String,
        parameters: Map<String, String>,
    ): AWSIoTProvisioningResponse
}
