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
import kotlinx.coroutines.flow.onEach
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
    /** 回数リセット */
    val resetAttempt: Boolean

    /**
     * 指定期間内でランダムな期間を返す.
     * @param range 指定期間
     * @return 遅延期間
     */
    fun randomBetween(range: LongRange): Duration

    /**
     * 以前の設定値を引き継いで、一部変更する.
     *
     * @param numRetries リトライ回数
     * @param base 基準間隔
     * @param maxDelay 最大遅延時間
     * @param factor 係数
     * @param resetAttempt 回数リセット
     */
    fun copy(
        numRetries: Long = this.numRetries,
        base: Duration = this.base,
        maxDelay: Duration = this.maxDelay,
        factor: Long = this.factor,
        resetAttempt: Boolean = this.resetAttempt,
    ): RetryPolicy

    companion object {
        val Default = create()

        fun create(
            numRetries: Long = Long.MAX_VALUE,
            base: Duration = 500.milliseconds,
            maxDelay: Duration = 15.minutes,
            factor: Long = 3L,
            resetAttempt: Boolean = false,
            random: Random = Random.Default,
        ): RetryPolicy {
            return RetryPolicyImpl(
                numRetries = numRetries,
                base = base,
                maxDelay = maxDelay,
                factor = factor,
                resetAttempt = resetAttempt,
                random = random,
            ).copy()
        }
    }
}

private data class RetryPolicyImpl(
    override val numRetries: Long,
    override val base: Duration,
    override val maxDelay: Duration,
    override val factor: Long,
    override val resetAttempt: Boolean,
    private val random: Random,
) : RetryPolicy {
    override fun randomBetween(range: LongRange): Duration {
        return random.nextLong(range).milliseconds
    }

    override fun copy(
        numRetries: Long,
        base: Duration,
        maxDelay: Duration,
        factor: Long,
        resetAttempt: Boolean
    ): RetryPolicy {
        return copy(
            numRetries = numRetries,
            base = base,
            maxDelay = maxDelay,
            factor = factor,
            resetAttempt = resetAttempt,
            random = random,
        )
    }
}

fun <T> Flow<T>.retryWithPolicy(
    retryPolicy: RetryPolicy = RetryPolicy.Default,
    predicate: suspend (cause: Throwable, attempt: Long) -> Boolean = { cause, _ -> cause is IOException },
): Flow<T> {
    val base = retryPolicy.base
    val factor = retryPolicy.factor
    val maxDelay = retryPolicy.maxDelay
    var delay = base
    var attempt = 0L
    return retryWhen { cause, _ ->
        if (predicate(cause, attempt) && attempt < retryPolicy.numRetries) {
            attempt++
            // Decorrelated jitter
            // sleep = min(cap, random_between(base, sleep * 3))
            val random = retryPolicy.randomBetween(base.inWholeMilliseconds.. delay.inWholeMilliseconds * factor)
            delay = minOf(maxDelay, random)
            delay(delay)
            return@retryWhen true
        }
        return@retryWhen false
    }.run {
        if (retryPolicy.resetAttempt) {
            onEach {
                delay = base
                attempt = 0L
            }
        } else this
    }
}
