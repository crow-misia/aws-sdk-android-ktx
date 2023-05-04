/**
 * Copyright (C) 2023 Zenichi Amano.
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
package io.github.crow_misia.aws.appsync.signv4

import com.amazonaws.DefaultRequest
import com.amazonaws.http.HttpMethodName
import com.apollographql.apollo3.api.http.ByteStringHttpBody
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import okio.Buffer
import java.net.URI

/**
 * Authorization Interceptor for AppSync.
 */
class AppSyncAuthorizationInterceptor(
    private val headerProcessor: HeaderProcessor,
) : HttpInterceptor {
    override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
        // Create request
        val dr = DefaultRequest<Any>(SERVICE_NAME)
        dr.setEndpoint(request)
        dr.headers = request.headers.associateBy({ it.name }, { it.value })
        dr.httpMethod = HttpMethodName.valueOf(request.method.name.uppercase())

        // read body
        val bodyBuffer = Buffer()
        request.body?.writeTo(bodyBuffer)

        val bodyContent = bodyBuffer.snapshot()

        // generate signature
        dr.content = ByteBufferInputStream(bodyContent.asByteBuffer())

        // append authorization headers
        headerProcessor.process(dr)

        val newRequest = HttpRequest.Builder(request.method, request.url)
            // copy signed/credentialed request to OkHttp request.
            .addHeaders(dr.headers.map { HttpHeader(it.key, it.value) })
            .body(ByteStringHttpBody(CONTENT_TYPE, bodyContent))
            .build()
        return chain.proceed(newRequest)
    }

    private fun DefaultRequest<*>.setEndpoint(request: HttpRequest) {
        val uri = URI.create(request.url)
        endpoint = URI(uri.scheme, uri.host, null ,null)
        resourcePath = uri.path
    }

    companion object {
        private const val CONTENT_TYPE = "application/json"
        private const val SERVICE_NAME = "appsync"
    }
}
