package io.github.crow_misia.aws.iot

import com.amazonaws.AmazonClientException
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

suspend fun AWSIotMqttManager.connectUsingALPN(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectUsingALPN(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

suspend fun AWSIotMqttManager.connectWithProxy(
    keyStore: KeyStore,
    proxyHost: String,
    proxyPort: Int,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connectWithProxy(keyStore, proxyHost, proxyPort, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

suspend fun AWSIotMqttManager.connect(
    keyStore: KeyStore,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(keyStore, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

suspend fun AWSIotMqttManager.connect(
    credentialsProvider: AWSCredentialsProvider,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(credentialsProvider, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

suspend fun AWSIotMqttManager.connect(
    tokenKeyName: String,
    token: String,
    tokenSignature: String,
    customAuthorizer: String,
): Flow<AWSIotMqttClientStatus> = callbackFlow {
    connect(tokenKeyName, token, tokenSignature, customAuthorizer, createConnectCallback(isAutoReconnect, ::resetReconnect))
    awaitClose { disconnectQuite() }
}

suspend fun AWSIotMqttManager.connect(
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

suspend fun AWSIotMqttManager.subscribe(
    topic: String,
    qos: AWSIotMqttQos,
    onDelivered: suspend () -> Unit = { },
): Flow<SubscribeData> = callbackFlow {
    subscribeToTopic(topic, qos, createSubscriptionStatusCallback { onDelivered() }) { topic, data ->
        trySend(SubscribeData(topic, data))
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
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
) = callbackFlow {
    publishString(str, topic, qos, createMessageDeliveryCallback { }, userData, isRetained)
    awaitClose()
}.first()

suspend fun AWSIotMqttManager.publish(
    data: ByteArray,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
) = callbackFlow {
    publishData(data, topic, qos, createMessageDeliveryCallback { }, userData, isRetained)
    awaitClose()
}.first()

suspend fun AWSIotMqttManager.publishWithReply(
    str: String,
    topic: String,
    qos: AWSIotMqttQos,
    userData: Any? = null,
    isRetained: Boolean = false,
): SubscribeData = callbackFlow {
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
