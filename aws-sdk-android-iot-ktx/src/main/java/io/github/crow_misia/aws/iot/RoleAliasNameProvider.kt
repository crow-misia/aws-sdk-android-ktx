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

import com.amazonaws.services.securitytoken.model.RoleAliasName

/**
 * RoleAliasName Provider.
 *
 * @param T Application Data Type
 */
fun interface RoleAliasNameProvider<T> {
    /**
     * Provide RoleAliasName.
     *
     * @param data Application Data
     * @return RoleAliasName
     */
    fun provide(data: T): RoleAliasName
}
