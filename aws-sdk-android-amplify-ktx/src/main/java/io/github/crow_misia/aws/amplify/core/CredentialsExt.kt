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

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.time.toSdkInstant
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSSessionCredentials
import io.github.crow_misia.aws.core.AWSTemporaryCredentials
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.util.Date

fun AWSCredentials.toAmplifyCredentials(expiration: Date? = null): com.amplifyframework.auth.AWSCredentials {
    val credentials = when (this) {
        is AWSTemporaryCredentials ->
            com.amplifyframework.auth.AWSCredentials.createAWSCredentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = this.expiration.toEpochMilliseconds(),
            )
        is AWSSessionCredentials ->
            com.amplifyframework.auth.AWSCredentials.createAWSCredentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = requireNotNull(expiration) { "needed expiration to convert." }.time,
            )
        else ->
            com.amplifyframework.auth.AWSCredentials.createAWSCredentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = null,
                expiration = null,
            )
    }
    return checkNotNull(credentials)
}

fun AWSCredentials.toSdkCredentials(expiration: Instant? = null): Credentials {
    val credentials = when (this) {
        is AWSTemporaryCredentials ->
            Credentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = this.expiration.toJavaInstant().toSdkInstant(),
            )
        is AWSSessionCredentials ->
            Credentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = requireNotNull(expiration) { "needed expiration to convert." }.toJavaInstant().toSdkInstant(),
            )
        else ->
            Credentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = null,
                expiration = null,
            )
    }
    return credentials
}

