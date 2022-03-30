package com.amazonaws.services.securitytoken.model

import java.io.Serializable

class AssumeRoleWithCredentialsResult : Serializable {
    companion object {
        const val serialVersionUID = 1L
    }

    var credentials: Credentials? = null
}
