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

import com.amazonaws.services.securitytoken.model.Credentials
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.SimpleTypeJsonUnmarshallers.DateJsonUnmarshaller
import com.amazonaws.transform.SimpleTypeJsonUnmarshallers.StringJsonUnmarshaller
import com.amazonaws.transform.TimestampFormat
import com.amazonaws.transform.Unmarshaller

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
                "expiration" -> credentials.expiration = DateJsonUnmarshaller.getInstance(TimestampFormat.ISO_8601).unmarshall(context)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return credentials
    }
}
