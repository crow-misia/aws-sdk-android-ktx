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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * リトライポリシー.
 */
interface RetryPolicy {
    /** リトライ回数 */
    val numRetries: Long
    /** 基準間隔 */
    val base: Duration
    /** 最大遅延時間 */
    val maxDelay: Duration
    /** 係数 */
    val factor: Long
    /**
     * 指定期間内でランダムな期間を返す.
     * @param range 指定期間
     * @return 遅延期間
     */
    fun randomBetween(range: LongRange): Duration

    companion object {
        val Default = create()

        fun create(
            numRetries: Long = Long.MAX_VALUE,
            base: Duration = 500.milliseconds,
            maxDelay: Duration = 15.minutes,
            factor: Long = 3L,
            random: Random = Random.Default
        ): RetryPolicy {
            return object : RetryPolicy {
                override val numRetries = numRetries
                override val base = base
                override val maxDelay = maxDelay
                override val factor = factor
                override fun randomBetween(range: LongRange): Duration {
                    return random.nextLong(range).milliseconds
                }
            }
        }
    }
}

inline fun <T> Flow<T>.retryWithPolicy(
    retryPolicy: RetryPolicy = RetryPolicy.Default,
    crossinline isTargetCause: (cause: Throwable, attempt: Long) -> Boolean = { cause, _ -> cause is IOException },
): Flow<T> {
    val base = retryPolicy.base
    val factor = retryPolicy.factor
    val maxDelay = retryPolicy.maxDelay
    var delay = base
    return retryWhen { cause, attempt ->
        if (isTargetCause(cause, attempt) && attempt < retryPolicy.numRetries) {
            // Decorrlated jitter
            // sleep = min(cap, random_between(base, sleep * 3))
            val random = retryPolicy.randomBetween(base.inWholeMilliseconds.. delay.inWholeMilliseconds * factor)
            delay = minOf(maxDelay, random)
            delay(delay)
            return@retryWhen true
        } else {
            return@retryWhen false
        }
    }
}
