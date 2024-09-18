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
package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.amazonaws.mobileconnectors.iot.AWSIotMqttSubscriptionStatusCallback
import io.github.crow_misia.aws.iot.publisher.MqttMessage
import io.github.crow_misia.aws.iot.publisher.TopicName
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.security.KeyStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val publishDefaultTimeout = 15.seconds

private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(manager: AWSIotMqttManager): AWSIotMqttClientStatusCallback {
    val isAutoReconnect = manager.isAutoReconnect
    if (isAutoReconnect) {
        manager.setCleanSession(true)
    }

    return AWSIotMqttClientStatusCallback { status, cause ->
        cause?.also {
            close(it)
            return@also
        }
        if (!isAutoReconnect && status == AWSIotMqttClientStatus.ConnectionLost) {
            close(AWSIoTMqttDisconnectException)
        } else {
            trySend(status)
        }
    }
}

private fun createSubscriptionStatusCallback(
    continuation: Continuation<Unit>,
) = object : AWSIotMqttSubscriptionStatusCallback {
    override fun onSuccess() {
        continuation.resume(Unit)
    }

    override fun onFailure(exception: Throwable) {
        continuation.resumeWithException(exception)
    }
}

private fun <T> createMessageDeliveryCallback(
    continuation: Continuation<T?>,
) = AWSIotMqttMessageDeliveryCallback { status, userData ->
    when (status) {
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success -> {
            @Suppress("UNCHECKED_CAST")
            continuation.resume(userData as? T)
        }
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Fail -> {
            continuation.resumeWithException(AWSIoTMqttDeliveryException("Error message delivery.", userData))
        }
        else -> {
            continuation.resumeWithException(AWSIoTMqttDeliveryException("Invalid status $status", userData))
        }
    }
}

fun AWSIotMqttManager.connectUsingALPN(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectUsingALPN(keyStore, createConnectCallback(this@connectUsingALPN))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback(this@connectWithProxy))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback(this@connect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback(this@connect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback(this@connect))
    awaitClose { disconnectQuite() }
}

fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback(this@connect))
    awaitClose { disconnectQuite() }
}

private fun AWSIotMqttManager.disconnectQuite() {
    runCatching {
        disconnect()
    }
}

fun AWSIotMqttManager.subscribe(
    topic: TopicName,
    qos: AWSIotMqttQos,
    onDelivered: suspend () -> Unit = { },
): Flow<SubscribeData> = subscribe(
    topics = listOf(topic),
    qos = qos,
    onDelivered = onDelivered,
)

fun AWSIotMqttManager.subscribe(
    topics: List<TopicName>,
    qos: AWSIotMqttQos,
    onDelivered: suspend () -> Unit = { },
) = callbackFlow {
    topics.forEach { topic ->
        suspendCoroutine {
            subscribeToTopic(topic.value, qos, createSubscriptionStatusCallback(it)) { topic, data ->
                trySend(SubscribeData(topic, data))
            }
        }
    }

    onDelivered()

    awaitClose {
        topics.forEach { topic ->
            runCatching {
                unsubscribeTopic(topic.value)
            }
        }
    }
}

suspend fun AWSIotMqttManager.publish(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    isRetained: Boolean = false,
    timeout: Duration = publishDefaultTimeout,
) = publish<Unit>(
    data = data,
    topic = topic,
    qos = qos,
    userData = null,
    isRetained = isRetained,
    timeout = timeout,
)

suspend fun AWSIotMqttManager.publish(
    message: MqttMessage,
    timeout: Duration = publishDefaultTimeout,
) = publish(
    data = message.data,
    topic = message.topicName,
    qos = message.qos,
    userData = message.userData,
    isRetained = message.retainMode.isRetained,
    timeout = timeout,
)

suspend fun <T> AWSIotMqttManager.publish(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    userData: T? = null,
    isRetained: Boolean = false,
    timeout: Duration = publishDefaultTimeout,
) = withTimeout(timeout) {
    suspendCoroutine<T?> {
        publishData(data, topic.value, qos, createMessageDeliveryCallback(it), userData, isRetained)
    }
}

suspend fun AWSIotMqttManager.publishWithReply(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    isRetained: Boolean = false,
    timeout: Duration = publishDefaultTimeout,
) = publishWithReply(
    data = data,
    topic = topic,
    qos = qos,
    userData = null,
    isRetained = isRetained,
    timeout = timeout,
)

suspend fun <T> AWSIotMqttManager.publishWithReply(
    data: ByteArray = EMPTY_BYTE_ARRAY,
    topic: TopicName,
    qos: AWSIotMqttQos,
    userData: T? = null,
    isRetained: Boolean = false,
    timeout: Duration,
): SubscribeData {
    val acceptedTopic = TopicName("${topic}/accepted")
    val rejectedTopic = TopicName("${topic}/rejected")

    // subscribe accepted/rejected topic
    return subscribe(topics = listOf(acceptedTopic, rejectedTopic), qos = qos) {
        publish(data, topic, qos, userData, isRetained, timeout)
    }.map {
        when (TopicName(it.topic)) {
            acceptedTopic -> Result.success(it)
            else -> Result.failure(AWSIoTMqttPublishWithReplyException(
                message = "Rejected",
                topic = it.topic,
                response = it.data,
                userData = userData,
            ))
        }
    }.catch {
        emit(Result.failure(it))
    }.first().getOrThrow()
}
