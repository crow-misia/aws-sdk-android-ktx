package com.amazonaws.services.securitytoken.model.transform

import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsResult
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.Unmarshaller

object AssumeRoleWithCredentialsResponseJsonMarshaller : Unmarshaller<AssumeRoleWithCredentialsResult, JsonUnmarshallerContext> {
    override fun unmarshall(context: JsonUnmarshallerContext): AssumeRoleWithCredentialsResult {
        val assumeRoleResult = AssumeRoleWithCredentialsResult()

        val reader = context.reader
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "credentials") {
                assumeRoleResult.credentials = CredentialsJsonUnmarshaller.unmarshall(context)
            } else {
                reader.skipValue()
            }
        }

        return assumeRoleResult
    }
}
