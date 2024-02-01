package io.github.crow_misia.aws.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class FlowExtTest : StringSpec({
    suspend fun test(
        retryPolicy: RetryPolicy,
        loop: Int,
        resultExpect: List<String>,
        attemptExpect: List<Long>,
        collector: FlowCollector<String>.(loopCount: Int) -> Unit,
    ) {
        val flowCount = AtomicInteger(0)
        val loopCount = AtomicInteger(0)
        val attemptResults = mutableListOf<Long>()
        val result = flow {
            flowCount.getAndIncrement()
            do {
                val currentLoopCount = loopCount.getAndIncrement()
                collector(currentLoopCount)
                emit("$flowCount $currentLoopCount")
            } while (loopCount.get() <= loop)
        }.retryWithPolicy(retryPolicy) { _, attempt ->
            attemptResults.add(attempt)
            return@retryWithPolicy true
        }.toList()
        result shouldBe resultExpect
        attemptResults shouldBe attemptExpect
    }

    "リトライ回数 リセットなし" {
        test(
            retryPolicy = RetryPolicy.create(base = 20.milliseconds, resetAttempt = false),
            loop = 4,
            // *の箇所で例外発生
            // 1 0(*), 2 1, 2 2(*), 3 3(*), 4 4
            resultExpect = listOf("2 1", "4 4"),
            attemptExpect = listOf(0L, 1L, 2L),
        ) {
            when (it) {
                0 -> throw IllegalStateException("other error")
                2 -> throw IllegalStateException("other error")
                3 -> throw IllegalStateException("other error")
            }
        }
    }

    "リトライ回数 リセットあり" {
        test(
            retryPolicy = RetryPolicy.create(base = 20.milliseconds, resetAttempt = true),
            loop = 3,
            // *の箇所で例外発生
            // 1 0(*), 2 1, 2 2(*), 3 3(*), 4 4
            resultExpect = listOf("2 1", "4 4"),
            attemptExpect = listOf(0L, 0L, 1L),
        ) {
            when (it) {
                0 -> throw IllegalStateException("other error")
                2 -> throw IllegalStateException("other error")
                3 -> throw IllegalStateException("other error")
            }
        }
    }
})
