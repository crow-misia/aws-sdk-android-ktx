/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Copyright 2023 Zenichi Amano.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 *
 * Based on com.amazonaws.http.JsonResponseHandler
 */
package io.github.crow_misia.aws.appsync

import com.amazonaws.AmazonWebServiceResponse
import com.amazonaws.ResponseMetadata
import com.amazonaws.http.HttpResponse
import com.amazonaws.http.HttpResponseHandler
import com.amazonaws.internal.CRC32MismatchException
import com.amazonaws.logging.LogFactory
import com.amazonaws.util.CRC32ChecksumCalculatingInputStream
import com.amazonaws.util.StringUtils
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.json.jsonReader
import com.apollographql.apollo3.api.parseJsonResponse
import com.apollographql.apollo3.api.parseResponse
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * AppSync Response Handler.
 */
class AppSyncResponseHandler<D : Operation.Data>(
    private val operation: Operation<D>,
    private val customScalarAdapters: CustomScalarAdapters,
) : HttpResponseHandler<AmazonWebServiceResponse<ApolloResponse<D>>> {
    private val log = LogFactory.getLog("com.amazonaws.request")

    @Suppress("MemberVisibilityCanBePrivate")
    var needsConnectionLeftOpen: Boolean = false

    override fun handle(response: HttpResponse): AmazonWebServiceResponse<ApolloResponse<D>> {
        log.trace("Parsing service response JSON")

        val crc32Checksum = response.headers["x-amz-crc32"]
        log.debug("CRC32Checksum = $crc32Checksum")
        val contentEncoding = response.headers["Content-Encoding"]
        log.debug("content encoding = $contentEncoding")

        // Get the raw content input stream to calculate the crc32 checksum on gzipped data.
        var content = response.rawContent ?: run {
            // An empty input stream to avoid NPE
            ByteArrayInputStream("{}".toByteArray(StringUtils.UTF8))
        }

        val isGzipEncoded = contentEncoding?.equals("gzip", true) ?: false

        // Handle various combinations of GZIP encoding and CRC checksums. Some services (e.g.,
        // DynamoDB) return a checksum with gzip encoding, some do not. We'll also cover the case
        // where a service returns a checksum for non-gzip encoding. The default case (not gzip
        // encoded, no checksum) is already handled: we'll just operate on the raw content stream.
        val checksumCalculatingInputStream = crc32Checksum?.let {
            CRC32ChecksumCalculatingInputStream(content).also { content = it }
        }

        if (isGzipEncoded) {
            content = GZIPInputStream(content)
        }

        val jsonReader = content.source().buffer().jsonReader()

        return try {
            val result = operation.parseResponse(jsonReader = jsonReader, customScalarAdapters = customScalarAdapters)

            checksumCalculatingInputStream?.crC32Checksum?.also { clientSideCRC ->
                val serverSideCRC = crc32Checksum.toLong()
                if (clientSideCRC != serverSideCRC) {
                    throw CRC32MismatchException("Client calculated crc32 checksum didn't match that calculated by server side")
                }
            }

            AmazonWebServiceResponse<ApolloResponse<D>>().also { awsResponse ->
                awsResponse.result = result
                val metadata = mapOf(
                    ResponseMetadata.AWS_REQUEST_ID to response.headers["x-amzn-RequestId"],
                )
                awsResponse.responseMetadata = ResponseMetadata(metadata)
                log.trace("Done parsing service response")
            }
        } finally {
            if (!needsConnectionLeftOpen) {
                runCatching {
                    jsonReader.close()
                }.onFailure {
                    log.warn("Error closing json parser", it)
                }
            }
        }
    }

    override fun needsConnectionLeftOpen(): Boolean = needsConnectionLeftOpen
}
