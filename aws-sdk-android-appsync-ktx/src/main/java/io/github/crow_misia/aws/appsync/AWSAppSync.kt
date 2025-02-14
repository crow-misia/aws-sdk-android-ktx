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
package io.github.crow_misia.aws.appsync

import com.amazonaws.regions.Region
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Query

/**
 * Interface of Amazon AppSync Service.
 */
interface AWSAppSync {
    /**
     * set endpoint.
     */
    fun setEndpoint(endpoint: String)

    /**
     * set region.
     */
    fun setRegion(region: Region)

    /**
     * Shutdown.
     */
    fun shutdown()

    /**
     * execute GraphQL Query.
     */
    fun <D : Query.Data> query(query: Query<D>): ApolloCall<D>

    /**
     * execute GraphQL Mutation.
     */
    fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloCall<D>
}
