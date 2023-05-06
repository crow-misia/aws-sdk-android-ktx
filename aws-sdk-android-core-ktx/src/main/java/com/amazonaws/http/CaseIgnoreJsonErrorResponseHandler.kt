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
package com.amazonaws.http

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.transform.JsonErrorUnmarshaller
import java.io.IOException

class CaseIgnoreJsonErrorResponseHandler(
    private val unmarshallerList: List<JsonErrorUnmarshaller>,
) : JsonErrorResponseHandler(unmarshallerList) {
    override fun handle(response: HttpResponse): AmazonServiceException? {
        val fixedHeaders = response.headers
            .mapKeys { (key, _) ->
                HEADERS[key.lowercase()] ?: key
            }
        val fixedResponse = HttpResponse.builder()
            .statusText(response.statusText)
            .statusCode(response.statusCode)
            .content(response.rawContent)
            .also {
                fixedHeaders.forEach { header -> it.header(header.key, header.value) }
            }
            .build()
        val error = try {
            JsonErrorResponse.fromResponse(fixedResponse)
        } catch (e: IOException) {
            throw AmazonClientException("Unable to parse error response", e)
        }

        val ase = runErrorUnmarshallers(error) ?: return null
        ase.statusCode = response.statusCode

        if (response.statusCode < HTTP_STATUS_INTERNAL_SERVER_ERROR) {
            ase.errorType = AmazonServiceException.ErrorType.Client
        } else {
            ase.errorType = AmazonServiceException.ErrorType.Service
        }
        ase.errorCode = error.errorCode

        fixedHeaders[X_AMZN_REQUEST_ID]?.also {
            ase.requestId = it
        }

        return ase
    }

    private fun runErrorUnmarshallers(error: JsonErrorResponse): AmazonServiceException? {
        return unmarshallerList.filter { it.match(error) }.firstNotNullOfOrNull { it.unmarshall(error) }
    }

    companion object {
        private const val X_AMZN_ERROR_TYPE = "x-amzn-ErrorType"
        private const val X_AMZN_REQUEST_ID = "x-amzn-RequestId"
        private const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500

        private val HEADERS = listOf(
            X_AMZN_ERROR_TYPE,
            X_AMZN_REQUEST_ID,
        ).associateBy { it.lowercase() }
    }
}
