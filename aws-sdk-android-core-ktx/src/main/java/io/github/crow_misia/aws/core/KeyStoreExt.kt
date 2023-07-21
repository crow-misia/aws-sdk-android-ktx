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
package io.github.crow_misia.aws.core

import java.io.ByteArrayOutputStream
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.encodingWith

/**
 * キーストアの内容をBase64エンコード文字列に変換する.
 *
 * @param password キーストアのパスワード
 */
@OptIn(ExperimentalEncodingApi::class)
fun KeyStore.asBase64(password: CharArray): String {
    return ByteArrayOutputStream().use { out ->
        out.encodingWith(Base64.Default).use {
            store(it, password)
        }
        out.toString()
    }
}