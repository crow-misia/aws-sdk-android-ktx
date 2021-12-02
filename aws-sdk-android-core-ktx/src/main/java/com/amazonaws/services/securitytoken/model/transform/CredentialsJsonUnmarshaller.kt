package com.amazonaws.services.securitytoken.model.transform

import com.amazonaws.services.securitytoken.model.Credentials
import com.amazonaws.transform.Unmarshaller
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.SimpleTypeJsonUnmarshallers.StringJsonUnmarshaller
import com.amazonaws.transform.SimpleTypeJsonUnmarshallers.DateJsonUnmarshaller

object CredentialsJsonUnmarshaller : Unmarshaller<Credentials, JsonUnmarshallerContext> {
    override fun unmarshall(context: JsonUnmarshallerContext): Credentials? {
        val reader = context.reader
        if (!reader.isContainer) {
            reader.skipValue()
            return null
        }

        val credentials = Credentials()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "accessKeyId" -> credentials.accessKeyId = StringJsonUnmarshaller.getInstance().unmarshall(context)
                "secretAccessKey" -> credentials.secretAccessKey = StringJsonUnmarshaller.getInstance().unmarshall(context)
                "sessionToken" -> credentials.sessionToken = StringJsonUnmarshaller.getInstance().unmarshall(context)
                "expiration" -> credentials.expiration = DateJsonUnmarshaller.getInstance().unmarshall(context)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return credentials
    }
}