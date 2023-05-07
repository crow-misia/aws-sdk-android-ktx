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
package io.github.crow_misia.aws.amplify.core

import com.amazonaws.AmazonClientException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amplifyframework.auth.AWSCredentials
import com.amplifyframework.auth.AuthException
import com.amplifyframework.core.Consumer

fun <T : AWSCredentials> AWSCredentialsProvider.toAmplifyCredentialProvider(): com.amplifyframework.auth.AWSCredentialsProvider<T> {
    return object : com.amplifyframework.auth.AWSCredentialsProvider<T> {
        @Suppress("UNCHECKED_CAST")
        override fun fetchAWSCredentials(onSuccess: Consumer<T>, onError: Consumer<AuthException>) {
            try {
                onSuccess.accept(credentials.toAmplifyCredentials() as T)
            } catch (e: AmazonClientException) {
                onError.accept(AuthException(e.message.orEmpty(), "", e))
            } catch (e: IllegalStateException) {
                onError.accept(AuthException(e.message.orEmpty(), "", e))
            }
        }
    }
}
