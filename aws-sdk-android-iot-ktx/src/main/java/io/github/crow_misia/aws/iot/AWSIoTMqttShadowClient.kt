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
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import io.github.crow_misia.aws.iot.model.DeviceShadowDeltaResponse
import io.github.crow_misia.aws.iot.model.DeviceShadowDocumentsResponse
import io.github.crow_misia.aws.iot.model.DeviceShadowErrorResponse
import io.github.crow_misia.aws.iot.model.DeviceShadowGetResponse
import io.github.crow_misia.aws.iot.model.DeviceShadowState
import io.github.crow_misia.aws.iot.model.DeviceShadowUpdateRequest
import io.github.crow_misia.aws.iot.model.DeviceShadowUpdateResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream

@OptIn(ExperimentalSerializationApi::class)
class AWSIoTMqttShadowClient internal constructor(
    private val manager: AWSIotMqttManager,
    private val thingNameProvider: () -> String,
    private val jsonFormat: Json,
) {
    constructor(
        manager: AWSIotMqttManager,
        thinsName: String,
        jsonFormat: Json = defaultJsonFormat,
    ): this(manager = manager, thingNameProvider = { thinsName }, jsonFormat = jsonFormat)

    fun disconnect() {
        manager.disconnect()
    }

    suspend inline fun <reified T> get(shadowName: String? = null): DeviceShadowGetResponse<T> {
        return get(
            serializer = serializer(),
            shadowName = shadowName,
        )
    }
    suspend fun <T> get(
        serializer: KSerializer<T>,
        shadowName: String? = null,
    ): DeviceShadowGetResponse<T> {
        return wrapError {
            val responseSerializer = DeviceShadowGetResponse.serializer(serializer)
            manager.publishWithReply(
                topic = getTopicName(shadowName, "get"),
                qos = AWSIotMqttQos.QOS1,
            ).let { jsonFormat.decodeFromStream(responseSerializer, it.inputStream()) }
        }
    }

    suspend inline fun <reified T> update(
        reported: T,
        shadowName: String? = null,
        clientToken: String? = null,
        version: Int? = null,
    ): DeviceShadowUpdateResponse<T> {
        return update(
            reported = reported,
            serializer = serializer(),
            shadowName = shadowName,
            clientToken = clientToken,
            version = version,
        )
    }

    suspend fun <T> update(
        reported: T,
        serializer: KSerializer<T>,
        shadowName: String? = null,
        clientToken: String? = null,
        version: Int? = null,
    ): DeviceShadowUpdateResponse<T> {
        val request = DeviceShadowUpdateRequest(
            state = DeviceShadowState(
                reported = reported,
            ),
            clientToken = clientToken,
            version = version,
        )
        return wrapError {
            val responseSerializer = DeviceShadowUpdateResponse.serializer(serializer)
            manager.publishWithReply(
                data = request.asByteArray(serializer),
                topic = getTopicName(shadowName, "update"),
                qos = AWSIotMqttQos.QOS1,
            ).let { jsonFormat.decodeFromStream(responseSerializer, it.inputStream()) }
        }
    }

    inline fun <reified T> subscribeDelta(shadowName: String? = null): Flow<DeviceShadowDeltaResponse<T>> {
        return subscribeDelta(
            serializer = serializer(),
            shadowName = shadowName,
        )
    }

    fun <T> subscribeDelta(
        serializer: KSerializer<T>,
        shadowName: String? = null,
    ): Flow<DeviceShadowDeltaResponse<T>> {
        val responseSerializer = DeviceShadowDeltaResponse.serializer(serializer)
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/delta"),
            qos = AWSIotMqttQos.QOS1,
        ).map { jsonFormat.decodeFromStream(responseSerializer, it.inputStream()) }
    }

    inline fun <reified T> subscribeDocuments(shadowName: String? = null): Flow<DeviceShadowDocumentsResponse<T>> {
        return subscribeDocuments(
            serializer = serializer(),
            shadowName = shadowName,
        )
    }

    fun <T> subscribeDocuments(
        serializer: KSerializer<T>,
        shadowName: String? = null,
    ): Flow<DeviceShadowDocumentsResponse<T>> {
        val responseSerializer = DeviceShadowDocumentsResponse.serializer(serializer)
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/documents"),
            qos = AWSIotMqttQos.QOS1,
        ).map { jsonFormat.decodeFromStream(responseSerializer, it.inputStream()) }
    }

    suspend fun delete(shadowName: String? = null) {
        wrapError {
            manager.publishWithReply(
                topic = getTopicName(shadowName, "delete"),
                qos = AWSIotMqttQos.QOS1,
            )
        }
    }

    private fun getTopicName(shadowName: String?, method: String): String {
        val thingName = thingNameProvider()

        return shadowName?.let {
            "\$aws/things/${thingName}/shadow/name/$shadowName/$method"
        } ?: "\$aws/things/${thingName}/shadow/$method"
    }

    private inline fun <T> wrapError(block: () -> T): T {
        return try {
            block()
        } catch (e: AWSIoTMqttPublishWithReplyException) {
            val errorResponse = jsonFormat.decodeFromStream<DeviceShadowErrorResponse>(ByteArrayInputStream(e.response))
            throw AWSIotDeviceShadowException(errorResponse)
        }
    }

    companion object {
        internal val defaultJsonFormat = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}

fun AWSIotMqttManager.asShadowClient(
    thingName: String,
    jsonFormat: Json = AWSIoTMqttShadowClient.defaultJsonFormat,
): AWSIoTMqttShadowClient {
    return AWSIoTMqttShadowClient(manager = this, thingNameProvider = { thingName }, jsonFormat = jsonFormat)
}

fun AWSIotMqttManager.asShadowClient(
    thingNameProvider: () -> String,
    jsonFormat: Json = AWSIoTMqttShadowClient.defaultJsonFormat,
): AWSIoTMqttShadowClient {
    return AWSIoTMqttShadowClient(this, thingNameProvider, jsonFormat)
}
