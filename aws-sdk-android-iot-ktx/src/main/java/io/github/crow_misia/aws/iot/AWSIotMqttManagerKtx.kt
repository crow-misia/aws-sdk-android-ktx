package io.github.crow_misia.aws.iot

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.KeyStore

@ExperimentalCoroutinesApi
private fun ProducerScope<AWSIotMqttClientStatus>.createConnectCallback(
    isAutoReconnect: Boolean,
    resetReconnect: () -> Unit,
): AWSIotMqttClientStatusCallback {
    return if (isAutoReconnect) {
        AWSIotMqttClientStatusCallback { status, _ ->
            trySend(status)
            // Give up reconnect.
            when (status) {
                AWSIotMqttClientStatus.ConnectionLost -> close()
                AWSIotMqttClientStatus.Connected -> resetReconnect()
                else -> {
                    // ignore.
                }
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
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    crossinline onSuccess: suspend () -> Unit,
) = object : AWSIotMqttSubscriptionStatusCallback {
    override fun onSuccess() {
        launch(defaultDispatcher) {
            onSuccess()
        }
    }

    override fun onFailure(exception: Throwable) {
        close(exception)
    }
}


@ExperimentalCoroutinesApi
private inline fun <T> ProducerScope<T>.createMessageDeliveryCallback(
    crossinline sendElement: () -> T,
) = AWSIotMqttMessageDeliveryCallback { status, userData ->
    when (status) {
        AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success -> {
            trySend(sendElement())
            close()
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
    connectUsingALPN(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.connect(
    username: String,
    password: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(username, password, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

private fun AWSIotMqttManager.disconnectQuite() {
    try {
        disconnect()
    } catch (e: Throwable) {
        // ignore.
    }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
    onDeliveried: suspend () -> Unit = { },
): Flow<SubscribeData> = callbackFlow {
    subscribeToTopic(topic, qos, createSubscriptionStatusCallback { onDeliveried() }) { topic, data ->
        trySend(SubscribeData(topic, data))
    }
    awaitClose {
        try {
            unsubscribeTopic(topic)
        } catch (e: Throwable) {
            // ignore.
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publish(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
) = callbackFlow<Unit> {
    publishString(str, topic, qos, createMessageDeliveryCallback { Unit }, userData, isRetained)
    awaitClose()
}.first()

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publish(
    data: ByteArray,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
) = callbackFlow<Unit> {
    publishData(data, topic, qos, createMessageDeliveryCallback { Unit }, userData, isRetained)
    awaitClose()
}.first()

@ExperimentalCoroutinesApi
suspend fun AWSIotMqttManager.publishWithReply(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): SubscribeData = callbackFlow<SubscribeData> {
    val acceptedTopic = "$topic/accepted"
    val rejectedTopic = "$topic/rejected"

    // for wait subscribe accepted/rejected topic
    val publisher = MutableSharedFlow<Unit>()
    val acceptedFlow = subscribe(acceptedTopic, qos) { publisher.emit(Unit) }
    val rejectedFlow = subscribe(rejectedTopic, qos) { publisher.emit(Unit) }

    val jobPublish = launch {
        publisher
            .drop(1)
            .collect {
                publish(str, topic, qos, userData, isRetained)
            }
    }
    val jobResponse = launch {
        val res = merge(acceptedFlow, rejectedFlow).first()
        when (res.topic) {
            acceptedTopic -> {
                trySend(res)
                close()
            }
            rejectedTopic -> {
                close(AWSIoTMqttPublishWithReplyException("Rejected", res.topic, res.data, userData))
            }
        }
    }

    awaitClose {
        jobResponse.cancel()
        jobPublish.cancel()
    }
}.first()
