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
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.http.HttpHeader
import java.io.IOException

/**
 * Header Processor.
 */
sealed interface HeaderProcessor {
    @Throws(IOException::class)
    fun process(request: DefaultRequest<*>)
}

class ApiKeyHeaderProcessor(
    private val apiKey: String,
    private val subscriberUUID: String? = null,
) : HeaderProcessor {
    override fun process(request: DefaultRequest<*>) {
        request.addHeader(X_API_KEY, apiKey)
        subscriberUUID?.also {
            request.addHeader(X_AMZ_SUBSCRIBER_ID, it)
        }
    }

    companion object {
        private const val X_AMZ_SUBSCRIBER_ID = "x-amz-subscriber-id"
        private const val X_API_KEY = "x-api-key"
    }
}

class CredentialsHeaderProcessor(
    private val signerProvider: SignerProvider,
    private val credentialsProvider: AWSCredentialsProvider,
) : HeaderProcessor {
    override fun process(request: DefaultRequest<*>) {
        val credentials = credentialsProvider.credentials
        val signer = signerProvider.provide()
        signer.sign(request, credentials)
    }
}

class CognitoUserPoolsHeaderProcessor(
    private val authProvider: AuthProvider,
) : HeaderProcessor {
    override fun process(request: DefaultRequest<*>) {
        request.addHeader(HttpHeader.AUTHORIZATION, authProvider.getLatestAuthToken())
    }
}

class OidcHeaderProcessor(
    private val authProvider: AuthProvider,
) : HeaderProcessor {
    override fun process(request: DefaultRequest<*>) {
        request.addHeader(HttpHeader.AUTHORIZATION, authProvider.getLatestAuthToken())
    }
}

class AwsLambdaHeaderProcessor(
    private val authProvider: AuthProvider,
) : HeaderProcessor {
    override fun process(request: DefaultRequest<*>) {
        request.addHeader(HttpHeader.AUTHORIZATION, authProvider.getLatestAuthToken())
    }
}
