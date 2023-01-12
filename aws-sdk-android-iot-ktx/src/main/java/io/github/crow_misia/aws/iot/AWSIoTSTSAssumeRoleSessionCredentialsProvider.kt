package io.github.crow_misia.aws.iot

import com.amazonaws.ClientConfiguration
import com.amazonaws.assumeRoleWithCredentials
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import io.github.crow_misia.aws.core.Okhttp3HttpClient
import io.github.crow_misia.aws.core.asBasicSessionCredentials
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date

open class AWSIoTSTSAssumeRoleSessionCredentialsProvider private constructor(
    private var thingName: String,
    private val securityTokenService: AWSSecurityTokenServiceClient,
) : AWSCredentialsProvider {
    companion object {
        /** Default duration for started sessions. */
        const val DEFAULT_DURATION_SECONDS = 900

        /** Time before expiry within which credentials will be renewed. */
        const val EXPIRY_TIME_MILLIS = 60 * 1000

        fun createOkhttp3Client(
            keystore: KeyStore,
            password: String,
            rootCa: X509Certificate,
            okHttpClient: OkHttpClient,
            clientConfiguration: ClientConfiguration,
        ): Okhttp3HttpClient {
            return Okhttp3HttpClient(clientConfiguration, okHttpClient).also {
                it.setKeyStore(keystore, password, rootCa)
            }
        }
    }

    /**
     * The current session credentials.
     */
    private var sessionCredentials: AWSSessionCredentials? = null

    /**
     * The expiration time for the current session credentials.
     */
    private var sessionCredentialsExpiration: Date? = null

    @JvmOverloads
    constructor(
        thingName: String,
        keystore: KeyStore,
        password: String = AWSIotKeystoreHelper.AWS_IOT_INTERNAL_KEYSTORE_PASSWORD,
        rootCa: X509Certificate,
        stsEndpoint: String,
        roleAliasName: String,
        okHttpClient: OkHttpClient,
        clientConfiguration: ClientConfiguration,
    ): this(
        thingName = thingName,
        client = createOkhttp3Client(keystore, password, rootCa, okHttpClient, clientConfiguration),
        stsEndpoint = stsEndpoint,
        roleAliasName = roleAliasName,
        clientConfiguration = clientConfiguration,
    )

    constructor(
        thingName: String,
        client: Okhttp3HttpClient,
        stsEndpoint: String,
        roleAliasName: String,
        clientConfiguration: ClientConfiguration,
    ): this(
        thingName = thingName,
        securityTokenService = AWSSecurityTokenServiceClient(
            StaticCredentialsProvider(AnonymousAWSCredentials()),
            clientConfiguration,
            client,
        ),
        stsEndpoint = stsEndpoint,
        roleAliasName = roleAliasName,
    )

    constructor(
        thingName: String,
        securityTokenService: AWSSecurityTokenServiceClient,
        stsEndpoint: String,
        roleAliasName: String,
    ): this(
        thingName = thingName,
        securityTokenService = securityTokenService.also {
            it.endpoint = "$stsEndpoint/role-aliases/$roleAliasName/"
        }
    )

    fun setThingName(thingName: String) {
        this.thingName = thingName
        sessionCredentials = null
    }

    fun setSTSClientEndpoint(endpoint: String) {
        securityTokenService.endpoint = endpoint
        sessionCredentials = null
    }

    override fun getCredentials(): AWSCredentials {
        if (neededNewSession()) {
            startSession()
        }
        return checkNotNull(sessionCredentials)
    }

    override fun refresh() {
        startSession()
    }

    private fun startSession() {
        val assumeRoleResult = securityTokenService.assumeRoleWithCredentials(
            AssumeRoleWithCredentialsRequest(thingName)
        )
        val stsCredentials = assumeRoleResult.credentials
        sessionCredentials = stsCredentials?.asBasicSessionCredentials()
        sessionCredentialsExpiration = stsCredentials?.expiration
    }

    private fun neededNewSession(): Boolean {
        sessionCredentials ?: return true
        val expiration = sessionCredentialsExpiration ?: return true

        val timeRemaining = expiration.time - System.currentTimeMillis()
        return timeRemaining < EXPIRY_TIME_MILLIS
    }
}