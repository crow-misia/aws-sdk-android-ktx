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
@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package io.github.crow_misia.aws.iot.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream

/**
 * 状態ドキュメント取得レスポンス.
 *
 * @property state 状態ドキュメント
 * @property metadata 属性ごとにタイムスタンプ
 * @property version 指定した場合、最新バージョンと一致すると更新処理が行われる
 * @property timestamp レスポンスがAWS IoTによって生成された日時
 */
@Serializable
data class DeviceShadowGetResponse<T>(
    val state: DeviceShadowState<T>,
    val metadata: DeviceShadowMetadata,
    val timestamp: Long? = null,
    val version: Int,
)

/**
 * 状態ドキュメント更新レスポンス.
 *
 * @property state 状態ドキュメント
 * @property clientToken クライアントトークン(指定するとレスポンスに含まれる)
 * @property version 指定した場合、最新バージョンと一致すると更新処理が行われる
 */
@Serializable
data class DeviceShadowUpdateRequest<T>(
    val state: DeviceShadowState<T>,
    val clientToken: String? = null,
    val version: Int? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun asByteArray(serializer: KSerializer<T>): ByteArray {
        val requestSerializer = serializer(serializer)
        return ByteArrayOutputStream().use {
            Json.encodeToStream(requestSerializer, this, it)
            it.toByteArray()
        }
    }
}

/**
 * 状態ドキュメント更新レスポンス.
 *
 * @property state 状態ドキュメント
 * @property metadata 属性ごとにタイムスタンプ
 * @property clientToken クライアントトークン(指定するとレスポンスに含まれる)
 * @property version 指定した場合、最新バージョンと一致すると更新処理が行われる
 * @property timestamp レスポンスがAWS IoTによって生成された日時
 */
@Serializable
data class DeviceShadowUpdateResponse<T>(
    val state: DeviceShadowState<T>,
    val metadata: DeviceShadowMetadata,
    val clientToken: String? = null,
    val timestamp: Long? = null,
    val version: Int,
)

/**
 * /delta レスポンス状態ドキュメント.
 *
 * @property state 状態ドキュメント
 * @property metadata 属性ごとにタイムスタンプ
 * @property clientToken クライアントトークン(指定するとレスポンスに含まれる)
 * @property version 指定した場合、最新バージョンと一致すると更新処理が行われる
 */
@Serializable
data class DeviceShadowDeltaResponse<T>(
    val state: T,
    val metadata: DeviceShadowMetadata?,
    val clientToken: String? = null,
    val timestamp: Long? = null,
    val version: Int,
)

/**
 * /documents レスポンス状態ドキュメント.
 *
 * @property previous 以前の状態ドキュメント
 * @property current 現在の状態ドキュメント
 * @property clientToken クライアントトークン(指定するとレスポンスに含まれる)
 * @property timestamp レスポンスがAWS IoTによって生成された日時
 */
@Serializable
data class DeviceShadowDocumentsResponse<T>(
    val previous: DeviceShadowStateHolder<T>?,
    val current: DeviceShadowStateHolder<T>?,
    val clientToken: String? = null,
    val timestamp: Long? = null,
)

/**
 * シャドウエラーレスポンス.
 *
 * @property code エラータイプを示すHTTPレスポンスコード
 * @property message 追加の情報を提供するテキストメッセージ
 * @property timestamp レスポンスがAWS IoTによって生成された日時
 * @property clientToken 発行されたメッセージ内のクライアントトークン
 */
@Serializable
data class DeviceShadowErrorResponse(
    val code: Int,
    val message: String,
    val clientToken: String? = null,
    val timestamp: Long? = null,
)

/**
 * 状態ドキュメント.
 *
 * @property state 状態ドキュメント
 * @property metadata 属性ごとにタイムスタンプ
 * @property version バージョン
 */
@Serializable
data class DeviceShadowStateHolder<T>(
    val state: DeviceShadowState<T>?,
    val metadata: DeviceShadowMetadata?,
    val version: Int,
)

/**
 * 状態ドキュメント.
 *
 * @property desired モノが reportedセクション内の任意のデータを報告し、リクエスト状態ドキュメントにあったフィールドだけを含む場合にのみ存在
 * @property reported デバイスが desired セクション内の任意のデータを報告し、リクエスト状態ドキュメントにあったフィールドだけを含む場合にのみ存在
 * @property delta desiredデータがシャドウの現在のreportedデータと異なる場合のみ存在
 */
@Serializable
data class DeviceShadowState<T>(
    val desired: T? = null,
    val reported: T? = null,
    val delta: T? = null,
)

/**
 * 状態ドキュメント.
 *
 * @property desired デバイスで更新がリクエストされたstateのプロパティと更新日時
 * @property reported デバイスによってレポートされたstateのプロパティと更新日時
 */
@Serializable
data class DeviceShadowMetadata(
    val desired: Map<String, DeviceShadowTimestamp>? = null,
    val reported: Map<String, DeviceShadowTimestamp>? = null,
)

/**
 * タイムスタンプ.
 *
 * @property timestamp 属性ごとの更新されたタイムスタンプ
 */
@Serializable
data class DeviceShadowTimestamp(
    val timestamp: Long,
)
