package io.github.crow_misia.aws_sdk_android_core_ktx

import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.securitytoken.model.Credentials

fun Credentials.asBasicSessionCredentials(): BasicSessionCredentials {
    return BasicSessionCredentials(accessKeyId, secretAccessKey, sessionToken)
}