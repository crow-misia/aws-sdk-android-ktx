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
@file:Suppress("unused", "CanBeParameter", "MemberVisibilityCanBePrivate")

package io.github.crow_misia.aws.iot

import com.amazonaws.AmazonClientException
import io.github.crow_misia.aws.iot.model.DeviceShadowErrorResponse
import io.github.crow_misia.aws.iot.model.ProvisioningErrorResponse

sealed class AWSIoTMqttException(
    message: String? = null,
    cause: Throwable? = null,
) : AmazonClientException(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
}

class AWSIoTMqttDeliveryException(
    message: String,
    val userData: Any? = null,
) : AWSIoTMqttException(message)

class AWSIoTMqttPublishWithReplyException(
    message: String,
    val topic: String,
    val response: ByteArray,
    val userData: Any? = null,
) : AWSIoTMqttException(message)

class AWSIoTProvisioningException(
    val response: ProvisioningErrorResponse,
    cause: Throwable? = null,
) : AWSIoTMqttException(response.errorMessage, cause)

class AWSIotDeviceShadowException(
    val response: DeviceShadowErrorResponse,
) : AWSIoTMqttException(response.message)

data object AWSIoTMqttDisconnectException : AWSIoTMqttException("client disconnected") {
    private fun readResolve(): Any = AWSIoTMqttDisconnectException
}
