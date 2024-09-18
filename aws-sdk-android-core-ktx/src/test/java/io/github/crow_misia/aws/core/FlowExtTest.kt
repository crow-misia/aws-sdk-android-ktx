package io.github.crow_misia.aws.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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
                0 -> error("other error")
                2 -> error("other error")
                3 -> error("other error")
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
                0 -> error("other error")
                2 -> error("other error")
                3 -> error("other error")
            }
        }
    }

    "リトライポリシーのコピー" {
        val base = RetryPolicy.create(
            numRetries = 100,
            base = 123.seconds,
            resetAttempt = true,
            factor = 4L,
            maxDelay = 456.seconds,
        )
        base.copy(numRetries = 123).base shouldBe 123.seconds
        base.copy(base = 1.seconds).numRetries shouldBe 100
        base.copy(factor = 5).resetAttempt shouldBe true
    }

    "merge and retry" {
        val attemptResults = mutableListOf<Long>()
        flowOf(1, 2, 3)
            .flatMapConcat {
                merge(flow {
                    while (true) {
                        delay(100)
                        emit(1)
                    }
                }, flow {
                    emit(2)
                    error("error")
                })
            }.retryWithPolicy(
                RetryPolicy.create(
                    base = 20.milliseconds,
                    resetAttempt = false,
                    numRetries = 3,
                )
            ) { _, attempt ->
                attemptResults.add(attempt)
                return@retryWithPolicy true
            }.toList()
    }
})
