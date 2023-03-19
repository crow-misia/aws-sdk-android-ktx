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

import com.amazonaws.AmazonClientException

sealed class AWSIoTMqttException : AmazonClientException {
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

class AWSIoTMqttDeliveryException(
    message: String,
    val userData: Any?,
) : AWSIoTMqttException(message)

class AWSIoTMqttPublishWithReplyException(
    message: String,
    topic: String,
    response: ByteArray,
    val userData: Any?,
) : AWSIoTMqttException("$message:\ntopic:$topic\nResponse:${String(response)}")
