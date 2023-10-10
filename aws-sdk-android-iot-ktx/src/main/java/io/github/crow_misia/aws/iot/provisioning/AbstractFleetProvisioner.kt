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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Abstract Fleet Provisioner.
 */
@OptIn(ExperimentalSerializationApi::class)
abstract class AbstractFleetProvisioner(
    protected val mqttManager: AWSIotMqttManager,
) : AWSIoTFleetProvisioner {
    @OptIn(FlowPreview::class)
    override suspend fun provisioningThing(
        templateName: String,
        parameters: Map<String, String>,
        timeout: Duration,
        context: CoroutineContext,
        connect: suspend AWSIotMqttManager.() -> Flow<AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus>,
    ): AWSIoTProvisioningResponse = channelFlow {
        val scope = CoroutineScope(context)

        connect(mqttManager)
            .catch {
                handleError(it)
            }
            // Wait until connected.
            .timeout(timeout)
            .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
            .take(1)
            .onEach {
                trySend(process(templateName, parameters))
            }.catch {
                handleError(it)
            }.onCompletion {
                if (!isClosedForSend) {
                    handleError(it)
                }
            }.launchIn(scope)

        awaitClose()
    }.first()

    private fun ProducerScope<*>.handleError(cause: Throwable?) {
        if (cause is AWSIoTMqttPublishWithReplyException) {
            val errorResponse = Cbor.decodeFromByteArray<ProvisioningErrorResponse>(cause.response)
            close(AWSIoTProvisioningException(errorResponse))
        } else {
            close(cause)
        }
    }

    abstract suspend fun process(
        templateName: String,
        parameters: Map<String, String>,
    ): AWSIoTProvisioningResponse
}
