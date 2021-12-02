package com.amazonaws.services.securitytoken

import com.amazonaws.AmazonServiceException
import com.amazonaws.transform.Unmarshaller
import org.w3c.dom.Node

internal fun AWSSecurityTokenServiceClient.getExceptionUnmarshallers(): List<Unmarshaller<AmazonServiceException, Node>> {
    return exceptionUnmarshallers
}
