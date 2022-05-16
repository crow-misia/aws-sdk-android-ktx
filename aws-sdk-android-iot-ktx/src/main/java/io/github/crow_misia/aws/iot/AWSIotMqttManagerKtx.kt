package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import java.security.KeyStore

@ExperimentalCoroutinesApi
private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(): AWSIotMqttClientStatusCallback {
    return AWSIotMqttClientStatusCallback { status, throwable ->
        throwable?.also {
            close(it)
        } ?: run {
            trySend(status)
        }
    }
}

@ExperimentalCoroutinesApi
private fun ProducerScope<*>.createSubscriptionStatusCallback() =
    object : AWSIotMqttSubscriptionStatusCallback {
        override fun onSuccess() {
            close()
        }

        override fun onFailure(exception: Throwable) {
            close(exception)
        }
    }


@ExperimentalCoroutinesApi
private inline fun ProducerScope<*>.createMessageDeliveryCallback(
    crossinline onSuccess: () -> Unit,
) = AWSIotMqttMessageDeliveryCallback { status, userData ->
    when (status) {
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Fail -> {
            close(AWSIoTMqttDeliveryException("Error message delivery.", userData))
        }
        else -> onSuccess()
    }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connectUsingALPN(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = channelFlow {
    connectUsingALPN(keyStore, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = channelFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback())
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
): Flow<ByteArray> = callbackFlow {
    subscribe(topic, qos) { trySend(it) }.collect()
    awaitClose()
}

@ExperimentalCoroutinesApi
private suspend fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
    newMessageCallback: (ByteArray) -> Unit,
): Flow<ByteArray> = callbackFlow {
    subscribeToTopic(
        topic,
        qos,
        createSubscriptionStatusCallback()
    ) { _, data -> newMessageCallback(data) }
    awaitClose()
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publish(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): Flow<ByteArray> = callbackFlow {
    publishString(str, topic, qos, createMessageDeliveryCallback { close() }, userData, isRetained)
    awaitClose()
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publish(
    data: ByteArray,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): Flow<ByteArray> = callbackFlow {
    publishData(data, topic, qos, createMessageDeliveryCallback { close() }, userData, isRetained)
    awaitClose()
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publishWithReply(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): Flow<ByteArray> = callbackFlow {
    val acceptedTopic = "$topic/accepted"
    val rejectedTopic = "$topic/rejected"

    subscribe(acceptedTopic, qos) {
        trySend(it)
        close()
    }.collect()

    subscribe(rejectedTopic, qos) {
        close(AWSIoTMqttPublishWithReplyException("Rejected", it, userData))
    }.collect()

    publishString(str, topic, qos, createMessageDeliveryCallback { }, userData, isRetained)

    awaitClose {
        unsubscribeTopic(acceptedTopic)
        unsubscribeTopic(rejectedTopic)
    }
}
