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
package io.github.crow_misia.aws.core

import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.auth.BasicSessionCredentials
import java.time.Instant

interface AWSTemporaryCredentials : AWSSessionCredentials {
    val expiration: Instant
}

/**
 *
 * @param awsAccessKey the AWS access key.
 * @param awsSecretKey the AWS secret key.
 * @param sessionToken the session token.
 * @param expiration the session expiration
 */
class BasicTemporaryCredentials(
    awsAccessKey: String,
    awsSecretKey: String,
    sessionToken: String,
    override val expiration: Instant,
) : BasicSessionCredentials(awsAccessKey, awsSecretKey, sessionToken), AWSTemporaryCredentials
