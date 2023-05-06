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
package io.github.crow_misia.aws.appsync.model

import com.amazonaws.AmazonServiceException

/**
 * Base exception of AppSync service.
 */
open class AppSyncServiceException(message: String) : AmazonServiceException(message)

/**
 * have not access to perform this operation this resource.
 */
class AccessDeniedException(message: String) : AppSyncServiceException(message)

/**
 * API key exceeded a limit.
 */
class ApiKeyLimitExceededException(message: String) : AppSyncServiceException(message)

/**
 * API key expiration must be set to a value between 1 and 365 days from creation.
 */
class ApiKeyValidityOutOfBoundsException(message: String) : AppSyncServiceException(message)

/**
 * GraphQL API exceeded a limit.
 */
class ApiLimitExceededException(message: String) : AppSyncServiceException(message)

/**
 * request is not well formed.
 */
class BadRequestException(message: String) : AppSyncServiceException(message)

/**
 * An internal AppSync error occurred.
 */
class InternalFailureException(message: String) : AppSyncServiceException(message)

/**
 * The resource specified in the request was not found.
 */
class NotFoundException(message: String) : AppSyncServiceException(message)

/**
 * Another modification is in progress at this time.
 */
class ConcurrentModificationException(message: String) : AppSyncServiceException(message)

/**
 * not authorized to perform this operation.
 */
class UnauthorizedException(message: String) : AppSyncServiceException(message)

/**
 * request exceeded a limit.
 */
class LimitExceededException(message: String) : AppSyncServiceException(message)

/**
 * GraphQL schema is not valid.
 */
class GraphQLSchemaException(message: String) : AppSyncServiceException(message)
