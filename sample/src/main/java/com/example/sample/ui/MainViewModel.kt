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
import com.amazonaws.regions.Region
import com.example.sample.R
import io.github.crow_misia.aws.iot.*
import io.github.crow_misia.aws.iot.keystore.BasicKeyStoreProvisioningManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {
    private val sharedPreferences = application.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val resources = application.resources
    private val assetManager = application.assets

    private val provider = AWSIotMqttManagerProvider.create(
        region = Region.getRegion(resources.getString(R.string.aws_region)),
        endpoint = AWSIotMqttManager.Endpoint.fromString(resources.getString(R.string.aws_endpoint)),
    )

    private val provisioningManager = BasicKeyStoreProvisioningManager(
        provider = provider,
        templateName = resources.getString(R.string.aws_template_name),
        keyStoreProvider = { AWSIoTKeystoreHelperExt.loadKeyStore(
            "provisioning",
            assetManager.open("certificate.pem").bufferedReader().readText(),
            assetManager.open("private.key").bufferedReader().readText(),
            AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
        ) }
    )

    private var thingName = sharedPreferences.getString("thingName", null).orEmpty()

    private var shadowClient: AWSIoTMqttShadowClient? = null

    override fun onCleared() {
        shadowClient?.disconnect()
        shadowClient = null

        provider.allDisconnect()

        super.onCleared()
    }

    private fun createMqttManager(): AWSIotMqttManager {
        return provider.provide(thingName)
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
            try {
                provisioningManager.provisioning(JSONObject().apply {
                    put("SerialNumber", serialNumber)
                }).apply {
                    saveCertificateAndPrivateKey(
                        keystorePath = keystorePathStr,
                        keystoreName = keystoreName,
                        keystorePassword = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD
                    )
                    sharedPreferences.edit {
                        putString("certificateId", certificateId)
                        putString("thingName", thingName)
                        this@MainViewModel.thingName = thingName
                    }
                    Timber.i("Registered things.\n%s", this)
                }
            } catch (e: Throwable) {
                Timber.e(e, "Error provisioning.")
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
                manager.asShadowClient(thingName).also {
                    this@MainViewModel.shadowClient = it
                }
            }
            manager.connect(keyStore)
                // waiting until connected.
                .filter { it == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected }
                .flatMapConcat {
                    shadowClient.subscribeDocuments()
                }
                .onEach { Timber.i("Received shadow data = %s", it) }
                .catch { e -> Timber.e(e, "Error subscribe shadow.") }
                .launchIn(this)
        }
    }

    fun onClickGetShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

            try {
                val data = shadowClient.get()
                Timber.i("Get shadow data = %s", data)
            } catch (e: Throwable) {
                Timber.e(e, "Error get shadow.")
            }
        }
    }

    private val counter = AtomicInteger(0)

    fun onClickUpdateShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

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

    fun onClickDeleteShadow() {
        viewModelScope.launch(Dispatchers.IO) {
            val shadowClient = shadowClient ?: return@launch

            try {
                shadowClient.delete()
                Timber.i("Deleted shadow data")
            } catch (e: Throwable) {
                Timber.e(e, "Error delete shadow.")
            }
        }
    }
}