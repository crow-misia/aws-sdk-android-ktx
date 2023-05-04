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
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription

/**
 * Interface of Amazon AppSync Service.
 */
interface AWSAppSync {
    /**
     * 接続先エンドポイントをセットする.
     */
    fun setEndpoint(endpoint: String)

    /**
     * 接続リージョンをセットする.
     */
    fun setRegion(region: Region)

    /**
     * GraphQLのQueryを実行する.
     */
    fun <D : Query.Data> query(query: Query<D>): ApolloCall<D>

    /**
     * GraphQLのMutationを実行する.
     */
    fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloCall<D>

    /**
     * GraphQLのQueryを実行する.
     */
    fun <D : Subscription.Data> subscription(subscription: Subscription<D>): ApolloCall<D>
}
