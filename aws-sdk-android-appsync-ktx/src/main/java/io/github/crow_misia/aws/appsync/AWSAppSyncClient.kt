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
package io.github.crow_misia.aws.appsync

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Region
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.network.okHttpClient
import io.github.crow_misia.aws.appsync.signv4.AppSyncAuthorizationInterceptor
import io.github.crow_misia.aws.appsync.signv4.CredentialsHeaderProcessor
import okhttp3.OkHttpClient

/**
 * Amazon AppSync Service Client.
 */
class AWSAppSyncClient @JvmOverloads constructor(
    endpoint: String = "appsync.amazonaws.com/graphql",
    apolloClient: ApolloClient,
    credentialsProvider: AWSCredentialsProvider,
    clientConfiguration: ClientConfiguration = ClientConfiguration(),
) : AmazonWebServiceClient(clientConfiguration), AWSAppSync {
    @Volatile
    private var delegated: ApolloClient

    init {
        serviceNameIntern = "appsync"
        super.setEndpoint(endpoint)

        delegated = apolloClient.newBuilder()
            .serverUrl(getEndpoint())
            .addHttpInterceptor(AppSyncAuthorizationInterceptor(
                headerProcessor = CredentialsHeaderProcessor({ signer }, credentialsProvider),
            ))
            .build()
    }

    @JvmOverloads
    constructor(
        endpoint: String = "https://appsync.amazonaws.com/graphql",
        okHttpClient: OkHttpClient,
        credentialsProvider: AWSCredentialsProvider,
        clientConfiguration: ClientConfiguration = ClientConfiguration(),
    ) : this(
        apolloClient = ApolloClient.Builder().serverUrl(endpoint).okHttpClient(okHttpClient).build(),
        credentialsProvider = credentialsProvider,
        clientConfiguration = clientConfiguration,
    )

    override fun setEndpoint(endpoint: String) {
        super.setEndpoint(endpoint)

        rebuildClient()
    }

    override fun setRegion(region: Region) {
        super.setRegion(region)

        rebuildClient()
    }

    override fun <D : Query.Data> query(query: Query<D>): ApolloCall<D> {
        return delegated.query(query)
    }

    override fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloCall<D> {
        return delegated.mutation(mutation)
    }

    override fun <D : Subscription.Data> subscription(subscription: Subscription<D>): ApolloCall<D> {
        return delegated.subscription(subscription)
    }

    private fun rebuildClient() {
        delegated = delegated.newBuilder()
            .serverUrl(getEndpoint())
            .build()
    }
}
