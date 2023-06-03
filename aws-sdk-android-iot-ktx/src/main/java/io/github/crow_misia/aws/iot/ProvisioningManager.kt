/**
 * Copyright (C) 2023 Zenichi Amano.
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
package io.github.crow_misia.aws.iot

import org.json.JSONObject

/**
 * Provisioning Manager.
 */
interface ProvisioningManager {
    /**
     * doing provisioning.
     *
     * @param serialNumber Device unique ID
     */
    @Deprecated(message = "remove in v0.18.0", replaceWith = ReplaceWith("this.provisioning(JSONObject().put(\"SerialNumber\", serialNumber))"))
    suspend fun provisioning(serialNumber: String): AWSIoTProvisioningResponse = provisioning(JSONObject().put("SerialNumber", serialNumber))

    /**
     * doing provisioning.
     *
     * @param parameters Provisioning Parameters
     */
    suspend fun provisioning(parameters: JSONObject): AWSIoTProvisioningResponse
}
