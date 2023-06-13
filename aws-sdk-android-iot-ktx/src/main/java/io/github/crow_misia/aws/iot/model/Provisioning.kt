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
package io.github.crow_misia.aws.iot.model

import kotlinx.serialization.Serializable

/**
 * 証明書署名リクエスト (CSR) から証明書を作成リクエスト.
 *
 * @property certificateSigningRequest PEM形式のCSR
 */
@Serializable
data class CreateCertificateFromCsrRequest(
    val certificateSigningRequest: String,
)

/**
 * 証明書署名リクエスト (CSR) から証明書を作成レスポンス.
 *
 * @property certificateOwnershipToken プロビジョニング中に証明書の所有権を証明するトークン
 * @property certificateId 証明書のID
 * @property certificatePem PEM形式の証明書データ
 */
@Serializable
data class CreateCertificateFromCsrResponse(
    val certificateOwnershipToken: String,
    val certificateId: String,
    val certificatePem: String,
)

/**
 * 新しいキーと証明書を作成レスポンス.
 *
 * @property certificateOwnershipToken プロビジョニング中に証明書の所有権を証明するトークン
 * @property certificateId 証明書のID
 * @property certificatePem PEM形式の証明書データ
 * @property privateKey プライベートキー
 */
@Serializable
data class CreateKeysAndCertificateResponse(
    val certificateOwnershipToken: String,
    val certificateId: String,
    val certificatePem: String,
    val privateKey: String,
)

/**
 * 事前定義されたテンプレートを使用してモノをプロビジョニングリクエスト.
 *
 * @property certificateOwnershipToken プロビジョニング中に証明書の所有権を証明するトークン
 * @property parameters 登録リクエストを評価するために事前プロビジョニングフックで使用されるデバイスからの、キーと値のペア
 */
@Serializable
data class RegisterThingRequest(
    val certificateOwnershipToken: String,
    val parameters: Map<String, String> = emptyMap(),
)

/**
 * 事前定義されたテンプレートを使用してモノをプロビジョニングレスポンス.
 *
 * @property deviceConfiguration テンプレートで定義されているデバイス設定
 * @property thingName プロビジョニング中に作成される IoT モノの名前
 */
@Serializable
data class RegisterThingResponse(
    val deviceConfiguration: Map<String, String>,
    val thingName: String,
)

/**
 * プロビジョニングエラーレスポンス.
 *
 * @property statusCode ステータスコード
 * @property errorCode エラーコード
 * @property errorMessage エラーメッセージ
 */
@Serializable
data class ProvisioningErrorResponse(
    val statusCode: Int,
    val errorCode: String,
    val errorMessage: String,
)
