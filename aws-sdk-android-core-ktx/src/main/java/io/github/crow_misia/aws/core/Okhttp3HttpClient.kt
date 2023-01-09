package io.github.crow_misia.aws.core

import com.amazonaws.ClientConfiguration
import com.amazonaws.http.HttpClient
import com.amazonaws.http.HttpHeader
import com.amazonaws.http.HttpRequest
import com.amazonaws.http.HttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class Okhttp3HttpClient(
    private val config: ClientConfiguration,
    private val client: OkHttpClient,
) : HttpClient {
    private var keyStore: KeyStore? = null
    private var password: String? = null
    private var caPublicKey: X509Certificate? = null

    fun setKeyStore(keyStore: KeyStore, password: String, caPublicKey: X509Certificate) {
        this.keyStore = keyStore
        this.password = password
        this.caPublicKey = caPublicKey
    }

    override fun execute(request: HttpRequest): HttpResponse {
        val newClient = createClient()
        val postRequest = createRequest(request)

        val response = newClient.newCall(postRequest).execute()

        return createHttpResponse(response)
    }

    private fun createClient(): OkHttpClient {
        return client.newBuilder().also {
            // configure the connection
            it.connectTimeout(config.connectionTimeout.toLong(), TimeUnit.MILLISECONDS)
            it.readTimeout(config.socketTimeout.toLong(), TimeUnit.MILLISECONDS)
            // disable redirect and cache
            it.cache(null)
            it.followRedirects(false)
            it.followSslRedirects(false)

            // client certificate
            setKeyStore(it)
        }.build()
    }

    private fun setKeyStore(builder: OkHttpClient.Builder) {
        val keyStore = keyStore ?: return
        val caPublicKey = caPublicKey ?: return

        val trustedStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustedStore.load(null)
        trustedStore.setCertificateEntry(caPublicKey.subjectX500Principal.name, caPublicKey)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustedStore)

        val trustManagers = trustManagerFactory.trustManagers
        check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected default trust managers:" + Arrays.toString(trustManagers)
        }
        val trustManager = trustManagers[0] as X509TrustManager

        val keyManagerFactory = KeyManagerFactory.getInstance("X509");
        keyManagerFactory.init(keyStore, password?.toCharArray())

        val sc = SSLContext.getInstance("TLS")
        sc.init(keyManagerFactory.keyManagers, null, null)
        builder.sslSocketFactory(sc.socketFactory, trustManager)
    }

    private fun createRequest(request: HttpRequest): Request {
        val builder = Request.Builder()
            .url(request.uri.toString())

        // add headers
        if (!request.headers.isNullOrEmpty()) {
            for ((key, value) in request.headers) {
                // Skip reserved headers for HttpURLConnection
                if (key != HttpHeader.CONTENT_LENGTH && key != HttpHeader.HOST) {
                    builder.addHeader(key, value)
                }
            }
        }

        val data = request.content?.readBytes()
        builder.method(request.method, data?.toRequestBody())

        return builder.build()
    }

    private fun createHttpResponse(response: Response): HttpResponse {
        return HttpResponse.builder().also {
            for ((key, value) in response.headers) {
                it.header(key, value)
            }
            response.body.also { body ->
                it.content(body.byteStream())
            }
            it.statusCode(response.code)
            it.statusText(response.message)
        }.build()
    }

    override fun shutdown() {
        // no op.
    }
}
