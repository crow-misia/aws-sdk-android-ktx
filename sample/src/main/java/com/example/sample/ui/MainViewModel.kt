package com.example.sample.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.viewModelScope
import com.amazonaws.mobileconnectors.iot.AWSIoTKeystoreHelperExt
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.loadPrivateKey
import com.amazonaws.mobileconnectors.iot.loadX509Certificate
import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.model.ThingName
import com.example.sample.R
import io.github.crow_misia.aws.core.retryWithPolicy
import io.github.crow_misia.aws.iot.AWSIoTMqttShadowClient
import io.github.crow_misia.aws.iot.AWSIotMqttManagerProvider
import io.github.crow_misia.aws.iot.ThingNameProvider
import io.github.crow_misia.aws.iot.asShadowClient
import io.github.crow_misia.aws.iot.connect
import io.github.crow_misia.aws.iot.keystore.BasicKeyStoreProvisioningManager
import io.github.crow_misia.aws.iot.provisioning.CreateCertificateFromCSRFleetProvisioner
import io.github.crow_misia.aws.iot.publisher.MqttMessageQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {
    private val sharedPreferences = application.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val resources = application.resources
    private val assetManager = application.assets

    private val provider = AWSIotMqttManagerProvider.create(
        region = Region.getRegion(resources.getString(R.string.aws_region)),
        endpoint = AWSIotMqttManager.Endpoint.fromString(resources.getString(R.string.aws_endpoint)),
    )

    private val provisioningManager = BasicKeyStoreProvisioningManager(
        mqttManagerProvider = provider,
        provisionerProvider = {
            CreateCertificateFromCSRFleetProvisioner(it)
        },
        templateName = resources.getString(R.string.aws_template_name),
        keyStoreProvider = {
            AWSIoTKeystoreHelperExt.loadKeyStore(
                certId = "provisioning",
                certificates = assetManager.loadX509Certificate("certificate.pem"),
                privateKey = assetManager.loadPrivateKey("private.key"),
            )
        }
    )

    private val thingNameProvider = ThingNameProvider<Unit> {
        ThingName(sharedPreferences.getString("thingName", null).orEmpty())
    }

    private var shadowClient: AWSIoTMqttShadowClient? = null
    private val messageQueue: MqttMessageQueue = MqttMessageQueue.createMessageQueue(messageExpired = 1.minutes)

    override fun onCleared() {
        shadowClient?.disconnect()
        shadowClient = null
        viewModelScope.launch {
            messageQueue.awaitUntilEmpty(1.seconds)
        }

        provider.allDisconnect()

        super.onCleared()
    }

    private fun createMqttManager(): AWSIotMqttManager {
        return provider.provide(thingNameProvider.provide(Unit))
    }

    fun onClickProvisioning() {
        val keystoreName = "iot"
        val keystorePath = getApplication<Application>().filesDir
        val keystorePathStr = keystorePath.absolutePath

        if (AWSIotKeystoreHelper.isKeystorePresent(keystorePathStr, keystoreName)) {
            Timber.i("Already exists device keystore.")
            return
        }

        val serialNumber = UUID.randomUUID().toString()
        viewModelScope.launch(context = Dispatchers.IO) {
            runCatching {
                provisioningManager.provisioning(
                    mapOf("SerialNumber" to serialNumber)
                )
            }.mapCatching {
                it.saveCertificateAndPrivateKey(
                    keystorePath = keystorePathStr,
                    keystoreName = keystoreName,
                )
                sharedPreferences.edit {
                    putString("certificateId", it.certificateId)
                    putString("thingName", it.thingName)
                }
                Timber.i("Registered things.\n%s", this)
            }.onFailure {
                Timber.e(it, "Error provisioning.")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun onClickSubscribeShadow() {
        val manager = createMqttManager()
        val keystoreName = "iot"
        val keystorePath = getApplication<Application>().filesDir
        val keystorePathStr = keystorePath.absolutePath
        val certId = sharedPreferences.getString("certificateId", null)
        val thingName = sharedPreferences.getString("thingName", null)

        if (certId.isNullOrBlank() ||
            thingName.isNullOrBlank() ||
            !AWSIotKeystoreHelper.isKeystorePresent(keystorePathStr, keystoreName)
        ) {
            return
        }

        val keyStore = AWSIotKeystoreHelper.getIotKeystore(
            certId,
            keystorePathStr,
            keystoreName,
            AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
        )
        viewModelScope.launch(Dispatchers.IO) {
            Timber.i("certId = %s, thingName = %s", certId, thingName)
            val shadowClient = shadowClient ?: run {
                manager.asShadowClient(ThingName(thingName)).also {
                    this@MainViewModel.shadowClient = it
                }
            }
            manager.connect(keyStore)
                // waiting until connected.
                .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
                .flatMapConcat {
                    combine(
                        messageQueue.asFlow(manager)
                            .onEach { message -> Timber.i("message queue received. $message") },
                        shadowClient.subscribeDocuments<SampleDeviceData>()
                    ) { _, _ -> }
                }
                .onEach { Timber.i("Received shadow data = %s", it) }
                .retryWithPolicy { e, _ ->
                    Timber.e(e, "error subscribe")
                    true
                }
                .launchIn(this)
        }
    }

    fun onClickSend() {
        viewModelScope.launch(Dispatchers.IO) {
            messageQueue.send(SampleMqttMessage(System.currentTimeMillis()))
        }
    }

    fun onClickGetShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

            runCatching {
                shadowClient.get<SampleDeviceData>()
            }.onSuccess {
                Timber.i("Get shadow data = %s", it)
            }.onFailure {
                Timber.e(it, "Error get shadow.")
            }
        }
    }

    private val counter = AtomicInteger(0)

    fun onClickUpdateShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

            runCatching {
                val count = counter.incrementAndGet()
                shadowClient.update(SampleDeviceData(count))
            }.onSuccess {
                Timber.i("Updated shadow data = %s", it)
            }.onFailure {
                Timber.e(it, "Error update shadow.")
            }
        }
    }

    fun onClickDeleteShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

            runCatching {
                shadowClient.delete()
            }.onSuccess {
                Timber.i("Deleted shadow data")
            }.onFailure {
                Timber.e(it, "Error delete shadow.")
            }
        }
    }

    @Serializable
    data class SampleDeviceData(
        val count: Int?,
    )
}
