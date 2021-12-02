package com.amazonaws

import com.amazonaws.http.DefaultErrorResponseHandler
import com.amazonaws.http.ExecutionContext
import com.amazonaws.http.JsonResponseHandler
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.getExceptionUnmarshallers
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsResult
import com.amazonaws.services.securitytoken.model.transform.*
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.Unmarshaller

@Throws(AmazonServiceException::class, AmazonClientException::class)
fun AWSSecurityTokenServiceClient.assumeRoleWithCredentials(assumeRoleRequest: AssumeRoleWithCredentialsRequest): AssumeRoleWithCredentialsResult {
    val executionContext = createExecutionContext(assumeRoleRequest)

    val request = AssumeRoleWithCredentialsRequestMarshaller().marshall(assumeRoleRequest)
    val response = invoke(
        request,
        AssumeRoleWithCredentialsResponseJsonMarshaller,
        executionContext
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

