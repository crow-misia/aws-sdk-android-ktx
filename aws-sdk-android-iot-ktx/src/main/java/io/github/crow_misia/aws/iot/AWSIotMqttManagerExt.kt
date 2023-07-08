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
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.security.KeyStore

@OptIn(DelicateCoroutinesApi::class)
private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(
    isAutoReconnect: Boolean,
    resetReconnect: () -> Unit,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
): AWSIotMqttClientStatusCallback {
    return AWSIotMqttClientStatusCallback { status, cause ->
        launch(defaultDispatcher) {
            if (isClosedForSend) {
                return@launch
            }
            if (isAutoReconnect) {
                send(status)
                // Give up reconnect.
                when (status) {
                    AWSIotMqttClientStatus.ConnectionLost -> close()
                    AWSIotMqttClientStatus.Connected -> resetReconnect()
                    else -> {
                        // ignore.
                    }
                }
            } else {
                send(status)
                cause?.also { cancel("MQTT Connection Error", it) } ?: run {
                    if (status == AWSIotMqttClientStatus.ConnectionLost) {
                        close()
                    }
                }
            }
        }
    }
}

private inline fun ProducerScope<*>.createSubscriptionStatusCallback(
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    crossinline onSuccess: suspend () -> Unit,
) = object : AWSIotMqttSubscriptionStatusCallback {
    override fun onSuccess() {
        launch(defaultDispatcher) {
            onSuccess()
        }
    }

    override fun onFailure(exception: Throwable) {
        cancel("Subscription failure.", exception)
    }
}


private fun ProducerScope<Unit>.createMessageDeliveryCallback(
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
): AWSIotMqttMessageDeliveryCallback {
    return AWSIotMqttMessageDeliveryCallback { status, userData ->
        when (status) {
            AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success -> {
                launch(defaultDispatcher) {
                    send(Unit)
                    close()
                }
            }
            AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Fail -> {
                close(AWSIoTMqttDeliveryException("Error message delivery.", userData))
            }
            else -> close(AWSIoTMqttDeliveryException("Invalid status $status", userData))
        }
    }
}

fun AWSIotMqttManager.connectUsingALPN(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectUsingALPN(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun AWSIotMqttManager.disconnectQuite() {
    try {
        disconnect()
    } catch (e: Throwable) {
        // ignore.
    }
}

fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
    onDelivered: suspend () -> Unit = { },
): Flow<SubscribeData> = callbackFlow {
    subscribeToTopic(topic, qos, createSubscriptionStatusCallback(onSuccess = onDelivered)) { topic, data ->
        launch {
            send(SubscribeData(topic, data))
        }
    }
    awaitClose {
        @Suppress("SwallowedException")
        try {
            unsubscribeTopic(topic)
        } catch (e: AmazonClientException) {
            // ignore.
        }
    }
}

suspend fun AWSIotMqttManager.publish(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
) = callbackFlow {
    publishData(data, topic, qos, createMessageDeliveryCallback(), userData, isRetained)
    awaitClose()
}.first()

suspend fun AWSIotMqttManager.publishWithReply(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): SubscribeData = callbackFlow {
    val acceptedTopic = "$topic/accepted"
    val rejectedTopic = "$topic/rejected"

    val scope = CoroutineScope(Job())

    // for wait subscribe accepted/rejected topic
    val publisher = MutableSharedFlow<Unit>()
    publisher
        .drop(1)
        .onEach {
            publish(data, topic, qos, userData, isRetained)
        }
        .launchIn(scope)

    // subscribe accepted topic
    subscribe(topic = acceptedTopic, qos = qos) { publisher.emit(Unit) }
        .onEach {
            send(it)
            close()
        }
        .launchIn(scope)
    // subscribe rejected topic
    subscribe(topic = rejectedTopic, qos = qos) { publisher.emit(Unit) }
        .onEach {
            close(AWSIoTMqttPublishWithReplyException("Rejected", it.topic, it.data, userData))
        }
        .launchIn(scope)

    awaitClose {
        scope.cancel()
    }
}.first()
