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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class AWSIoTMqttShadowClient internal constructor(
    private val manager: AWSIotMqttManager,
    private val thingNameProvider: () -> String,
) {
    constructor(manager: AWSIotMqttManager, thinsName: String): this(manager = manager, thingNameProvider = { thinsName })

    fun disconnect() {
        manager.disconnect()
    }

    suspend fun get(shadowName: String? = null): JSONObject {
        return manager.publishWithReply(
            str = "",
            topic = getTopicName(shadowName, "get"),
            qos = AWSIotMqttQos.QOS1,
        ).let { (_, data) ->
            JSONObject(String(data))
        }
    }

    suspend fun update(reported: JSONObject, shadowName: String? = null): JSONObject {
        val state = JSONObject().apply {
            put("state", JSONObject().apply {
                put("reported", reported)
            })
        }
        return manager.publishWithReply(
            str = state.toString(),
            topic = getTopicName(shadowName, "update"),
            qos = AWSIotMqttQos.QOS1,
        ).let { (_, data) ->
            JSONObject(String(data))
        }
    }

    fun subscribeDelta(shadowName: String? = null): Flow<JSONObject> {
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/delta"),
            qos = AWSIotMqttQos.QOS1,
        ).map { (_, data) ->
            JSONObject(String(data))
        }
    }

    fun subscribeDocuments(shadowName: String? = null): Flow<JSONObject> {
        return manager.subscribe(
            topic = getTopicName(shadowName, "update/documents"),
            qos = AWSIotMqttQos.QOS1,
        ).map { (_, data) ->
            JSONObject(String(data))
        }
    }

    suspend fun delete(shadowName: String? = null) {
        manager.publishWithReply(
            str = "",
            topic = getTopicName(shadowName, "delete"),
            qos = AWSIotMqttQos.QOS1,
        ).let { (_, data) ->
            JSONObject(String(data))
        }
    }

    private fun getTopicName(shadowName: String?, method: String): String {
        val thingName = thingNameProvider()

        return shadowName?.let {
            "\$aws/things/${thingName}/shadow/name/$shadowName/$method"
        } ?: "\$aws/things/${thingName}/shadow/$method"
    }
}

fun AWSIotMqttManager.asShadowClient(thingName: String): AWSIoTMqttShadowClient {
    return AWSIoTMqttShadowClient(this, thingName)
}
