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

import android.util.Base64
import java.security.SecureRandom

/**
 * Base64エンコードのランダムな文字列を生成する.
 *
 * @param n 生成するランダムデータの大きさ. 実際に出力される文字列の長さは、nの4/3程度になります
 * @return Base64エンコード文字列
 */
fun SecureRandom.nextBase64(n: Int): String {
    val tmp = ByteArray(n)
    nextBytes(tmp)
    return Base64.encodeToString(tmp, Base64.NO_WRAP)
}
