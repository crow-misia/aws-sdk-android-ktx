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
@file:Suppress("unused")

package io.github.crow_misia.aws.iot

import aws.smithy.kotlin.runtime.time.Clock
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import com.amazonaws.services.securitytoken.model.ThingName
import io.github.crow_misia.aws.core.AWSTemporaryCredentials
import io.github.crow_misia.aws.core.asAWSCredentials
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

open class AWSIoTSTSAssumeRoleSessionCredentialsProvider(
    private val clock: Clock = Clock.System,
    private val thingNameProvider: ThingNameProvider<Unit>,
    private val roleAliasNameProvider: RoleAliasNameProvider<ThingName>,
    private val securityTokenService: AWSIoTSecurityTokenServiceClient,
    private val expiryTime: Duration = 60.milliseconds,
) : AWSCredentialsProvider {
    /**
     * The current temporary credentials.
     */
    private var temporaryCredentials: AWSTemporaryCredentials? = null

    override fun getCredentials(): AWSCredentials {
        if (neededNewSession()) {
            startSession()
        }
        return checkNotNull(temporaryCredentials)
    }

    override fun refresh() {
        startSession()
    }

    private fun startSession() {
        val thingName = thingNameProvider.provide(Unit)
        val assumeRoleResult = securityTokenService.assumeRoleWithCredentials(
            AssumeRoleWithCredentialsRequest(
                thingName = thingName,
                roleAliasName = roleAliasNameProvider.provide(thingName),
            )
        )
        val stsCredentials = assumeRoleResult.credentials
        temporaryCredentials = stsCredentials.asAWSCredentials()
    }

    private fun neededNewSession(): Boolean {
        val credentials = temporaryCredentials ?: return true
        val expiration = credentials.expiration

        val timeRemaining = expiration - clock.now()
        return timeRemaining < expiryTime
    }
}
