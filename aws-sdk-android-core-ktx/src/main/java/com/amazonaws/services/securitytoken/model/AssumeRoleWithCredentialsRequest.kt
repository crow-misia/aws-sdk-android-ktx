package com.amazonaws.services.securitytoken.model

import com.amazonaws.AmazonWebServiceRequest
import java.io.Serializable

class AssumeRoleWithCredentialsRequest(
    val thingName: String,
) : AmazonWebServiceRequest(), Serializable {
    companion object {
        const val serialVersionUID = 1L
    }
}
