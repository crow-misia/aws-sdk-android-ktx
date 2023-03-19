/**
 * Copyright (C) 2021 Zenichi Amano.
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
package com.amazonaws

import com.amazonaws.http.DefaultErrorResponseHandler
import com.amazonaws.http.ExecutionContext
import com.amazonaws.http.JsonResponseHandler
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.getExceptionUnmarshallers
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsResult
import com.amazonaws.services.securitytoken.model.transform.*
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.Unmarshaller

fun AWSSecurityTokenService.assumeRoleWithCredentials(assumeRoleRequest: AssumeRoleWithCredentialsRequest): AssumeRoleWithCredentialsResult {
    require(this is AWSSecurityTokenServiceClient) {
        "this class it not AWSSecurityTokenServiceClient"
    }
    return assumeRoleWithCredentials(assumeRoleRequest)
}

@Throws(AmazonServiceException::class, AmazonClientException::class)
fun AWSSecurityTokenServiceClient.assumeRoleWithCredentials(assumeRoleRequest: AssumeRoleWithCredentialsRequest): AssumeRoleWithCredentialsResult {
    val executionContext = createExecutionContext(assumeRoleRequest)

    val request = AssumeRoleWithCredentialsRequestMarshaller().marshall(assumeRoleRequest)
    val response = invoke(
        request,
        AssumeRoleWithCredentialsResponseJsonMarshaller,
        executionContext,
    )
    return response.awsResponse
}

internal fun <X, Y : AmazonWebServiceRequest> AWSSecurityTokenServiceClient.invoke(
    request: Request<Y>,
    unmarshaller: Unmarshaller<X, JsonUnmarshallerContext>,
    executionContext: ExecutionContext
): Response<X> {
    request.endpoint = endpoint
    request.timeOffset = timeOffset

    val responseHandler = JsonResponseHandler(unmarshaller)
    val errorResponseHandler = DefaultErrorResponseHandler(getExceptionUnmarshallers())
    return client.execute(request, responseHandler, errorResponseHandler, executionContext)
}

