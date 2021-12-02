package com.amazonaws.services.securitytoken.model.transform

import com.amazonaws.AmazonClientException
import com.amazonaws.DefaultRequest
import com.amazonaws.Request
import com.amazonaws.http.HttpMethodName
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import com.amazonaws.transform.Marshaller
import com.amazonaws.util.StringUtils

class AssumeRoleWithCredentialsRequestMarshaller : Marshaller<Request<AssumeRoleWithCredentialsRequest>, AssumeRoleWithCredentialsRequest> {
    override fun marshall(assumeRoleRequest: AssumeRoleWithCredentialsRequest?): Request<AssumeRoleWithCredentialsRequest> {
        assumeRoleRequest ?: run {
            throw AmazonClientException("Invalid argument passed to marshall(AssumeRoleWithCredentialsRequest)")
        }


        val request = DefaultRequest<AssumeRoleWithCredentialsRequest>(assumeRoleRequest, "AWSSecurityTokenService")
        request.httpMethod = HttpMethodName.GET
        request.addHeader("x-amzn-iot-thingname", assumeRoleRequest.thingName)
        request.setEncodedResourcePath("credentials")

        assumeRoleRequest.durationSeconds?.also {
            request.addParameter("DurationSeconds", StringUtils.fromInteger(assumeRoleRequest.durationSeconds))
        }

        return request
    }
}