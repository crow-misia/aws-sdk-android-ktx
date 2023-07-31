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
import io.github.crow_misia.aws.iot.publisher.TopicName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.security.KeyStore

@OptIn(DelicateCoroutinesApi::class)
private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(
    context: CoroutineDispatcher = Dispatchers.Default,
): AWSIotMqttClientStatusCallback {
    return AWSIotMqttClientStatusCallback { status, cause ->
        launch(context) {
            if (isClosedForSend) {
                return@launch
            }
            send(status)
            cause?.also { close(it) } ?: run {
                if (status == AWSIotMqttClientStatus.ConnectionLost) {
                    close()
                }
            }
        }
    }
}

private inline fun ProducerScope<*>.createSubscriptionStatusCallback(
    context: CoroutineDispatcher = Dispatchers.Default,
    crossinline onSuccess: suspend () -> Unit,
) = object : AWSIotMqttSubscriptionStatusCallback {
    override fun onSuccess() {
        launch(context) {
            onSuccess()
        }
    }

    override fun onFailure(exception: Throwable) {
        close(exception)
    }
}


private fun <T> ProducerScope<T?>.createMessageDeliveryCallback(
    context: CoroutineDispatcher = Dispatchers.Default,
): AWSIotMqttMessageDeliveryCallback {
    return AWSIotMqttMessageDeliveryCallback { status, userData ->
        when (status) {
            AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success -> {
                launch(context) {
                    @Suppress("UNCHECKED_CAST")
                    send(userData as? T)
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
    connectUsingALPN(keyStore, createConnectCallback())
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback())
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback())
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback())
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback())
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback())
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
    topic: TopicName,
    qos: AWSIotMqttQos,
    onDelivered: suspend () -> Unit = { },
): Flow<SubscribeData> = callbackFlow {
    subscribeToTopic(topic.value, qos, createSubscriptionStatusCallback(onSuccess = onDelivered)) { topic, data ->
        launch {
            send(SubscribeData(topic, data))
        }
    }
    awaitClose {
        @Suppress("SwallowedException")
        try {
            unsubscribeTopic(topic.value)
        } catch (e: AmazonClientException) {
            // ignore.
        }
    }
}

suspend fun AWSIotMqttManager.publish(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    isRetained: Boolean = false,
) = publish<Unit>(
    data = data,
    topic = topic,
    qos = qos,
    userData = null,
    isRetained = isRetained,
)

suspend fun <T> AWSIotMqttManager.publish(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    userData: T? = null,
    isRetained: Boolean = false,
) = callbackFlow {
    publishData(data, topic.value, qos, createMessageDeliveryCallback<T>(), userData, isRetained)
    awaitClose()
}.first()

suspend fun AWSIotMqttManager.publishWithReply(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    isRetained: Boolean = false,
    context: CoroutineDispatcher = Dispatchers.IO,
): SubscribeData = publishWithReply<Unit>(
    data = data,
    topic = topic,
    qos = qos,
    userData = null,
    isRetained = isRetained,
    context = context,
)

suspend fun <T> AWSIotMqttManager.publishWithReply(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    userData: T? = null,
    isRetained: Boolean = false,
    context: CoroutineDispatcher = Dispatchers.IO,
): SubscribeData = callbackFlow {
    val acceptedTopic = TopicName("${topic.value}/accepted")
    val rejectedTopic = TopicName("${topic.value}/rejected")

    val job = Job()
    val scope = CoroutineScope(context + job)

    // for wait subscribe accepted/rejected topic
    val publisher = MutableSharedFlow<Unit>()
    publisher
        .drop(1)
        .onEach { publish(data, topic, qos, userData, isRetained) }
        .launchIn(scope)

    // subscribe accepted topic
    subscribe(topic = acceptedTopic, qos = qos) { publisher.emit(Unit) }
        .catch { close(it) }
        .onEach {
            send(it)
            close()
        }
        .launchIn(scope)
    // subscribe rejected topic
    subscribe(topic = rejectedTopic, qos = qos) { publisher.emit(Unit) }
        .catch { close(it) }
        .onEach {
            close(AWSIoTMqttPublishWithReplyException("Rejected", it.topic, it.data, userData))
        }
        .launchIn(scope)

    awaitClose {
        job.cancel()
    }
}.first()
