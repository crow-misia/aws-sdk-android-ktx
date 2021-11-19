package com.example.sample.ui.main

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.regions.Region
import com.example.sample.R
import io.github.crow_misia.aws_sdk_android_iot_ktx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val resources = application.resources

    private val region = Region.getRegion(resources.getString(R.string.aws_region))
    private val endpoint =
        AWSIotMqttManager.Endpoint.fromString(resources.getString(R.string.aws_endpoint))

    private val provider = AWSIotMqttManagerProvider.create(region, endpoint)

    private var thingName = sharedPreferences.getString("thingName", null).orEmpty()

    private var shadowClient: AWSIoTMqttShadowClient? = null

    override fun onCleared() {
        shadowClient?.disconnect()
        shadowClient = null

        provider.allDisconnect()

        super.onCleared()
    }

    private fun createMqttManagerForProvisioning(): AWSIotMqttManager {
        // temporary client id
        val clientId = UUID.randomUUID().toString().let {
            AWSIotMqttManager.ClientId.fromString(it)
        }
        return provider.provide(clientId)
    }

    private fun createMqttManager(): AWSIotMqttManager {
        return provider.provide(thingName)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onClickProvisioning() {
        val manager = createMqttManagerForProvisioning()
        val provisioningKeystoreName = "provisioning"
        val keystoreName = "iot"
        val keystorePath = getApplication<Application>().filesDir
        val keystorePathStr = keystorePath.absolutePath

        keystorePath.resolve(provisioningKeystoreName).delete()
        if (AWSIotKeystoreHelper.isKeystorePresent(keystorePathStr, keystoreName)) {
            Timber.i("Already exists device keystore.")
            return
        }

        // delete keystore for provisioning
        keystorePath.resolve(provisioningKeystoreName).delete()

        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
            "provisioning",
            resources.openRawResource(R.raw.certificate).bufferedReader().readText(),
            resources.openRawResource(R.raw.privatekey).bufferedReader().readText(),
            keystorePath.absolutePath,
            provisioningKeystoreName,
            AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
        )

        val templateName = resources.getString(R.string.aws_template_name)
        val serialNumber = UUID.randomUUID().toString()
        val keyStore = AWSIotKeystoreHelper.getIotKeystore(
            "provisioning",
            keystorePathStr,
            provisioningKeystoreName,
            AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    manager.provisioningThing(
                        keyStore,
                        templateName,
                        mapOf("SerialNumber" to serialNumber)
                    )
                        .collect {
                            it.saveCertificateAndPrivateKey(
                                keystorePathStr,
                                keystoreName,
                                AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
                            )
                            sharedPreferences.edit {
                                putString("certificateId", it.certificateId)
                                putString("thingName", it.thingName)
                                this@MainViewModel.thingName = it.thingName
                            }
                            Timber.i("Registered things.\n%s", it)
                        }
                } catch (e: Throwable) {
                    Timber.e(e, "Error provisioning.")
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onClickSubscribeShadow() {
        val manager = createMqttManager()
        val keystoreName = "iot"
        val keystorePath = getApplication<Application>().filesDir
        val keystorePathStr = keystorePath.absolutePath
        val certId = sharedPreferences.getString("certificateId", null)
        val thingName = sharedPreferences.getString("thingName", null)

        if (certId.isNullOrBlank() || thingName.isNullOrBlank() || !AWSIotKeystoreHelper.isKeystorePresent(
                keystorePathStr,
                keystoreName
            )
        ) {
            return
        }

        val keyStore = AWSIotKeystoreHelper.getIotKeystore(
            certId,
            keystorePathStr,
            keystoreName,
            AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
        )
        viewModelScope.launch {
            Timber.i("certId = %s, thingName = %s", certId, thingName)
            val shadowClient = shadowClient ?: run {
                manager.asShadowClient(thingName).also {
                    this@MainViewModel.shadowClient = it
                }
            }
            withContext(Dispatchers.IO) {
                manager.connect(keyStore)
                    // waiting until connected.
                    .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
                    .take(1)
                    .flatMapConcat {
                        shadowClient.subscribeDocuments()
                    }
                    .onEach { Timber.i("Received shadow data = %s", it) }
                    .catch { e -> Timber.e(e, "Error subscribe shadow.") }
                    .launchIn(this)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onClickGetShadow() {
        viewModelScope.launch {
            val shadowClient = shadowClient ?: return@launch

            withContext(Dispatchers.IO) {
                try {
                    val data = shadowClient.get()
                    Timber.i("Get shadow data = %s", data)
                } catch (e: Throwable) {
                    Timber.e(e, "Error get shadow.")
                }
            }
        }
    }

    private val counter = AtomicInteger(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onClickUpdateShadow() {
        viewModelScope.launch {
            val shadowClient = shadowClient ?: return@launch

            withContext(Dispatchers.IO) {
                try {
                    val count = counter.incrementAndGet()
                    val data = shadowClient.update(JSONObject().apply {
                        put("count", count)
                    })
                    Timber.i("Updated shadow data = %s", data)
                } catch (e: Throwable) {
                    Timber.e(e, "Error update shadow.")
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onClickDeleteShadow() {
        viewModelScope.launch {
            val shadowClient = shadowClient ?: return@launch

            withContext(Dispatchers.IO) {
                try {
                    shadowClient.delete()
                    Timber.i("Deleted shadow data")
                } catch (e: Throwable) {
                    Timber.e(e, "Error delete shadow.")
                }
            }
        }
    }
}
