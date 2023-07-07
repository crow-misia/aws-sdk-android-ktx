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
package com.amazonaws.services.securitytoken.model.transform

import com.amazonaws.AmazonClientException
import com.amazonaws.DefaultRequest
import com.amazonaws.Request
import com.amazonaws.http.HttpMethodName
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import com.amazonaws.transform.Marshaller

object AssumeRoleWithCredentialsRequestMarshaller : Marshaller<Request<AssumeRoleWithCredentialsRequest>, AssumeRoleWithCredentialsRequest> {
    override fun marshall(assumeRoleRequest: AssumeRoleWithCredentialsRequest?): Request<AssumeRoleWithCredentialsRequest> {
        assumeRoleRequest ?: run {
            throw AmazonClientException("Invalid argument passed to marshall(AssumeRoleWithCredentialsRequest)")
        }

        val request = DefaultRequest<AssumeRoleWithCredentialsRequest>(assumeRoleRequest, "AWSSecurityTokenService")
        request.httpMethod = HttpMethodName.GET
        request.addHeader("x-amzn-iot-thingname", assumeRoleRequest.thingName.name)
        request.resourcePath = "credentials"

        return request
    }
}
