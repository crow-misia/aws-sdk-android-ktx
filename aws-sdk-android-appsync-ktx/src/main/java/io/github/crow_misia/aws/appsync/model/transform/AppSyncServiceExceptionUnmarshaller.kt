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
package io.github.crow_misia.aws.appsync.model.transform

import com.amazonaws.http.JsonErrorResponseHandler
import com.amazonaws.transform.JsonErrorUnmarshaller
import io.github.crow_misia.aws.appsync.model.AccessDeniedException
import io.github.crow_misia.aws.appsync.model.ApiKeyLimitExceededException
import io.github.crow_misia.aws.appsync.model.ApiKeyValidityOutOfBoundsException
import io.github.crow_misia.aws.appsync.model.ApiLimitExceededException
import io.github.crow_misia.aws.appsync.model.AppSyncServiceException
import io.github.crow_misia.aws.appsync.model.BadRequestException
import io.github.crow_misia.aws.appsync.model.ConcurrentModificationException
import io.github.crow_misia.aws.appsync.model.GraphQLSchemaException
import io.github.crow_misia.aws.appsync.model.InternalFailureException
import io.github.crow_misia.aws.appsync.model.LimitExceededException
import io.github.crow_misia.aws.appsync.model.NotFoundException
import io.github.crow_misia.aws.appsync.model.UnauthorizedException

/**
 * AppSync Service exception unmarshaller.
 */
object AppSyncServiceExceptionUnmarshaller : JsonErrorUnmarshaller(AppSyncServiceException::class.java)

object AccessDeniedExceptionUnmarshaller : JsonErrorUnmarshaller(AccessDeniedException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "AccessDeniedException" == error.errorCode
}

object ApiKeyLimitExceededExceptionUnmarshaller : JsonErrorUnmarshaller(ApiKeyLimitExceededException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "ApiKeyLimitExceededException" == error.errorCode
}

object ApiKeyValidityOutOfBoundsExceptionUnmarshaller : JsonErrorUnmarshaller(ApiKeyValidityOutOfBoundsException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "ApiKeyValidityOutOfBoundsException" == error.errorCode
}

object ApiLimitExceededExceptionUnmarshaller : JsonErrorUnmarshaller(ApiLimitExceededException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "ApiLimitExceededException" == error.errorCode
}

object BadRequestExceptionUnmarshaller : JsonErrorUnmarshaller(BadRequestException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "BadRequestException" == error.errorCode
}

object InternalFailureExceptionUnmarshaller : JsonErrorUnmarshaller(InternalFailureException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "InternalFailureException" == error.errorCode
}

object NotFoundExceptionUnmarshaller : JsonErrorUnmarshaller(NotFoundException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "NotFoundException" == error.errorCode
}

object ConcurrentModificationExceptionUnmarshaller : JsonErrorUnmarshaller(ConcurrentModificationException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "ConcurrentModificationException" == error.errorCode
}

object UnauthorizedExceptionUnmarshaller : JsonErrorUnmarshaller(UnauthorizedException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "UnauthorizedException" == error.errorCode
}

object LimitExceededExceptionUnmarshaller : JsonErrorUnmarshaller(LimitExceededException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "LimitExceededException" == error.errorCode
}

object GraphQLSchemaExceptionUnmarshaller : JsonErrorUnmarshaller(GraphQLSchemaException::class.java) {
    override fun match(error: JsonErrorResponseHandler.JsonErrorResponse) = "GraphQLSchemaException" == error.errorCode
}
