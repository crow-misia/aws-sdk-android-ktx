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

import android.os.Build
import androidx.annotation.RequiresApi
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import aws.smithy.kotlin.runtime.time.toSdkInstant
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSSessionCredentials
import io.github.crow_misia.aws.core.AWSTemporaryCredentials
import java.util.Date

fun AWSCredentials.toAmplifyCredentials(expirationMillis: Long? = null): com.amplifyframework.auth.AWSCredentials {
    val credentials = when (this) {
        is AWSTemporaryCredentials ->
            com.amplifyframework.auth.AWSCredentials.createAWSCredentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = expiration.epochMilliseconds,
            )
        is AWSSessionCredentials ->
            com.amplifyframework.auth.AWSCredentials.createAWSCredentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = requireNotNull(expirationMillis) { "needed expiration to convert." },
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
fun AWSCredentials.toAmplifyCredentials(expiration: Date?): com.amplifyframework.auth.AWSCredentials {
    return toAmplifyCredentials(expiration?.time)
}
fun AWSCredentials.toAmplifyCredentials(expiration: Instant?): com.amplifyframework.auth.AWSCredentials {
    return toAmplifyCredentials(expiration?.epochMilliseconds)
}
@RequiresApi(Build.VERSION_CODES.O)
fun AWSCredentials.toAmplifyCredentials(expiration: java.time.Instant?): com.amplifyframework.auth.AWSCredentials {
    return toAmplifyCredentials(expiration?.toEpochMilli())
}

fun AWSCredentials.toSdkCredentials(expiration: Instant? = null): Credentials {
    val credentials = when (this) {
        is AWSTemporaryCredentials ->
            Credentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = this.expiration,
            )
        is AWSSessionCredentials ->
            Credentials(
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretKey,
                sessionToken = sessionToken,
                expiration = requireNotNull(expiration) { "needed expiration to convert." },
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
fun AWSCredentials.toSdkCredentials(expiration: java.time.Instant?): Credentials {
    return toSdkCredentials(expiration?.toSdkInstant())
}
fun AWSCredentials.toSdkCredentials(expiration: Date?): Credentials {
    return toSdkCredentials(expiration?.time?.let { Instant.fromEpochMilliseconds(it) })
}
fun AWSCredentials.toSdkCredentials(expirationMillis: Long?): Credentials {
    return toSdkCredentials(expirationMillis?.let { Instant.fromEpochMilliseconds(it) })
}
