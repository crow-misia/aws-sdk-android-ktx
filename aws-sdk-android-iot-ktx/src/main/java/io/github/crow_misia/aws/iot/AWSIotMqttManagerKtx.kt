package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.KeyStore

@ExperimentalCoroutinesApi
private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(isAutoReconnect: Boolean): AWSIotMqttClientStatusCallback {
    return if (isAutoReconnect) {
        AWSIotMqttClientStatusCallback { status, _ ->
            trySend(status)
            // Give up reconnect.
            if (status == AWSIotMqttClientStatus.ConnectionLost) {
                close()
            }
        }
    } else {
        AWSIotMqttClientStatusCallback { status, throwable ->
            throwable?.also {
                close(it)
            } ?: run {
                trySend(status)
            }
        }
    }
}

@ExperimentalCoroutinesApi
private inline fun ProducerScope<*>.createSubscriptionStatusCallback(
    crossinline onSuccess: () -> Unit,
) = object : AWSIotMqttSubscriptionStatusCallback {
    override fun onSuccess() {
        onSuccess()
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
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success -> {
            onSuccess()
        }
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Fail -> {
            close(AWSIoTMqttDeliveryException("Error message delivery.", userData))
        }
        else -> close(AWSIoTMqttDeliveryException("Invalid status $status", userData))
    }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connectUsingALPN(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectUsingALPN(keyStore, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback(isAutoReconnect))
    awaitClose { disconnect() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
): Flow<SubscribeData> = callbackFlow {
    subscribeToTopic(
        topic,
        qos,
        createSubscriptionStatusCallback { },
    ) { topic, data -> trySend(SubscribeData(topic, data)) }
    awaitClose { unsubscribeTopic(topic) }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publish(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): Flow<Unit> = callbackFlow {
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
): Flow<Unit> = callbackFlow {
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
): Flow<SubscribeData> = callbackFlow {
    val acceptedTopic = "$topic/accepted"
    val rejectedTopic = "$topic/rejected"

    val acceptedFlow = subscribe(acceptedTopic, qos)
    val rejectedFlow = subscribe(rejectedTopic, qos)

    val job = launch {
        merge(acceptedFlow, rejectedFlow)
            .onStart {
                publishString(str, topic, qos, createMessageDeliveryCallback { }, userData, isRetained)
            }
            .take(1)
            .collect {
                when (it.topic) {
                    acceptedTopic -> {
                        trySend(it)
                        close()
                    }
                    rejectedTopic -> {
                        close(AWSIoTMqttPublishWithReplyException("Rejected", it.topic, it.data, userData))
                    }
                }
            }
    }

    awaitClose {
        job.cancel()
    }
}
