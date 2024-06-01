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
import com.amazonaws.Request
import com.amazonaws.Response
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.http.CaseIgnoreJsonErrorResponseHandler
import com.amazonaws.http.HttpClient
import com.amazonaws.http.UrlHttpClient
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.transform.JsonErrorUnmarshaller
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.network.NetworkTransport
import io.github.crow_misia.aws.appsync.model.transform.AccessDeniedExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.ApiKeyLimitExceededExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.ApiKeyValidityOutOfBoundsExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.ApiLimitExceededExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.AppSyncRequestMarshaller
import io.github.crow_misia.aws.appsync.model.transform.AppSyncServiceExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.BadRequestExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.ConcurrentModificationExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.GraphQLSchemaExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.InternalFailureExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.LimitExceededExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.NotFoundExceptionUnmarshaller
import io.github.crow_misia.aws.appsync.model.transform.UnauthorizedExceptionUnmarshaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Amazon AppSync Service Client.
 */
class AWSAppSyncClient @JvmOverloads constructor(
    private val credentialsProvider: AWSCredentialsProvider,
    clientConfiguration: ClientConfiguration = ClientConfiguration(),
    httpClient: HttpClient = UrlHttpClient(clientConfiguration),
    clientBuilder: (ApolloClient.Builder) -> Unit = { }
) : AmazonWebServiceClient(clientConfiguration, httpClient), AWSAppSync {
    private val delegated: ApolloClient

    private val jsonErrorUnmarshallers: List<JsonErrorUnmarshaller> = listOf(
        AccessDeniedExceptionUnmarshaller,
        ApiKeyLimitExceededExceptionUnmarshaller,
        ApiKeyValidityOutOfBoundsExceptionUnmarshaller,
        ApiLimitExceededExceptionUnmarshaller,
        BadRequestExceptionUnmarshaller,
        InternalFailureExceptionUnmarshaller,
        NotFoundExceptionUnmarshaller,
        ConcurrentModificationExceptionUnmarshaller,
        UnauthorizedExceptionUnmarshaller,
        LimitExceededExceptionUnmarshaller,
        GraphQLSchemaExceptionUnmarshaller,
        AppSyncServiceExceptionUnmarshaller,
    )

    init {
        serviceNameIntern = SERVICE_NAME

        delegated = ApolloClient.Builder()
            .networkTransport(HttpClientNetworkTransport(this))
            .also(clientBuilder)
            .build()
    }

    constructor(
        credentials: AWSCredentials,
        clientConfiguration: ClientConfiguration = ClientConfiguration(),
        httpClient: HttpClient = UrlHttpClient(clientConfiguration),
    ) : this(StaticCredentialsProvider(credentials), clientConfiguration, httpClient)

    override fun <D : Query.Data> query(query: Query<D>): ApolloCall<D> {
        return delegated.query(query)
    }

    override fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloCall<D> {
        return delegated.mutation(mutation)
    }

    internal fun <D : Operation.Data> execute(request: ApolloRequest<D>): ApolloResponse<D> {
        val customScalarAdapters = checkNotNull(request.executionContext[CustomScalarAdapters])
        val wrappedRequest = AppSyncRequest(request)
        val operation = request.operation
        val dr = AppSyncRequestMarshaller(operation, customScalarAdapters).marshall(wrappedRequest)
        return invoke(dr, operation, customScalarAdapters).awsResponse
    }

    private operator fun <D : Operation.Data> invoke(
        request: Request<AppSyncRequest<D>>,
        operation: Operation<D>,
        customScalarAdapters: CustomScalarAdapters,
    ): Response<ApolloResponse<D>> {
        request.endpoint = endpoint
        request.timeOffset = timeOffset
        val executionContext = createExecutionContext(request).also {
            it.credentials = credentialsProvider.credentials
        }
        val responseHandler = AppSyncResponseHandler(operation, customScalarAdapters)
        val errorResponseHandler = CaseIgnoreJsonErrorResponseHandler(
            jsonErrorUnmarshallers
        )
        return client.execute(request, responseHandler, errorResponseHandler, executionContext)
    }

    companion object {
        private const val SERVICE_NAME = "appsync"
    }
}

class HttpClientNetworkTransport(
    private val client: AWSAppSyncClient,
) : NetworkTransport {
    override fun dispose() {
        // nop.
    }

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> {
        return flow {
            emit(client.execute(request))
        }
    }
}
