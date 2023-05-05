/**
 * Copyright (C) 2021 Zenichi Amano.
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
package io.github.crow_misia.aws.core

import com.amazonaws.http.HttpClient
import com.amazonaws.http.HttpHeader
import com.amazonaws.http.HttpRequest
import com.amazonaws.http.HttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Okhttp3HttpClient(private var client: OkHttpClient) : HttpClient {
    override fun execute(request: HttpRequest): HttpResponse {
        val postRequest = createRequest(request)

        val response = client.newCall(postRequest).execute()

        return createHttpResponse(response)
    }

    private fun createRequest(request: HttpRequest): Request {
        val builder = Request.Builder()
            .url(request.uri.toString())

        // add headers
        val headers = request.headers
        if (!headers.isNullOrEmpty()) {
            headers.forEach { (key, value) ->
                // Skip reserved headers for HttpURLConnection
                if (key != HttpHeader.CONTENT_LENGTH && key != HttpHeader.HOST) {
                    builder.addHeader(key, value)
                }
            }
        }

        builder.method(request.method, request.content?.toRequestBody())

        return builder.build()
    }

    private fun createHttpResponse(response: Response): HttpResponse {
        return HttpResponse.builder().also {
            response.headers.forEach { (key, value) ->
                it.header(key, value)
            }
            it.content(response.body.byteStream())
            it.statusCode(response.code)
            it.statusText(response.message)
        }.build()
    }

    override fun shutdown() {
        // no op.
    }
}
