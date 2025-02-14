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
package io.github.crow_misia.aws.appsync.model.transform

import com.amazonaws.DefaultRequest
import com.amazonaws.Request
import com.amazonaws.http.HttpMethodName
import com.amazonaws.transform.Marshaller
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.composeJsonRequest
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import io.github.crow_misia.aws.appsync.AppSyncRequest
import io.github.crow_misia.aws.core.asInputStream
import okio.Buffer

/**
 * AppSync Request Marshaller.
 */
class AppSyncRequestMarshaller<D : Operation.Data>(
    private val operation: Operation<D>,
    private val customScalarAdapters: CustomScalarAdapters,
) : Marshaller<Request<AppSyncRequest<D>>, AppSyncRequest<D>> {
    override fun marshall(request: AppSyncRequest<D>): Request<AppSyncRequest<D>> {
        val original = request.originalRequest
        val dr = DefaultRequest<AppSyncRequest<D>>(request, "appsync")
        dr.resourcePath = "graphql"
        dr.httpMethod = original.httpMethod.toHttpMethodName()
        dr.headers = original.httpHeaders?.associateBy({ it.name }, {it.value }).orEmpty()
        if (!dr.headers.containsKey("Content-Type")) {
            dr.addHeader("Content-Type", "application/json")
        }

        val buffer = Buffer()
        val jsonWriter = BufferedSinkJsonWriter(buffer)
        operation.composeJsonRequest(jsonWriter, customScalarAdapters)
        dr.content = buffer.readByteString().asByteBuffer().asInputStream()
        return dr
    }
}

private fun HttpMethod?.toHttpMethodName(): HttpMethodName {
    return when (this) {
        HttpMethod.Get -> HttpMethodName.GET
        HttpMethod.Post -> HttpMethodName.POST
        else -> HttpMethodName.POST
    }
}
