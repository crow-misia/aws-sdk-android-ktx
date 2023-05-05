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

import com.amazonaws.services.securitytoken.model.AssumeRoleWithCredentialsResult
import com.amazonaws.services.securitytoken.model.Credentials
import com.amazonaws.transform.JsonUnmarshallerContext
import com.amazonaws.transform.Unmarshaller

object AssumeRoleWithCredentialsResponseJsonMarshaller : Unmarshaller<AssumeRoleWithCredentialsResult, JsonUnmarshallerContext> {
    override fun unmarshall(context: JsonUnmarshallerContext): AssumeRoleWithCredentialsResult? {
        var credentials: Credentials? = null
        val reader = context.reader

        if (!reader.isContainer) {
            reader.skipValue()
            return null
        }

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "credentials") {
                credentials = CredentialsJsonUnmarshaller.unmarshall(context)
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        return credentials?.let { AssumeRoleWithCredentialsResult(it) }
    }
}
