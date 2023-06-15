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
import com.amazonaws.transform.Marshaller
import com.amazonaws.util.DateUtils
import com.amazonaws.util.json.JsonUtils
import java.io.StringWriter

object CredentialsJsonMarshaller : Marshaller<String, Credentials> {
    override fun marshall(credentials: Credentials): String {
        val stringWriter = StringWriter()
        val jsonWriter = JsonUtils.getJsonWriter(stringWriter)
        jsonWriter.beginObject()

        jsonWriter.name("accessKeyId")
        jsonWriter.value(credentials.accessKeyId)

        jsonWriter.name("secretAccessKey")
        jsonWriter.value(credentials.secretAccessKey)

        jsonWriter.name("sessionToken")
        jsonWriter.value(credentials.sessionToken)

        jsonWriter.name("expiration")
        jsonWriter.value(DateUtils.formatISO8601Date(credentials.expiration))

        jsonWriter.endObject()
        jsonWriter.close()
        return stringWriter.toString()
    }
}
