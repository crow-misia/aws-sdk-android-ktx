package io.github.crow_misia.aws.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class FlowExtTest : StringSpec({
    "リトライ回数 リセットなし" {
        val results = mutableListOf<Long>()
        flowOf(1, 2, 3)
            .onEach {
                if (it == 3) {
                    error("dummy error")
                }
            }
            .retryWithPolicy { _, attempt ->
                results.add(attempt)
                return@retryWithPolicy true
            }
            .take(7)
            .collect {
                println(it)
            }
        results shouldBe listOf(0L, 1L, 2L)
    }

    "リトライ回数 リセットあり" {
        val results = mutableListOf<Long>()
        flowOf(1, 2, 3)
            .onEach {
                if (it == 3) {
                    error("dummy error")
                }
            }
            .retryWithPolicy(RetryPolicy.create(resetAttempt = true)) { _, attempt ->
                results.add(attempt)
                return@retryWithPolicy true
            }
            .take(7)
            .collect {
                println(it)
            }
        results shouldBe listOf(0L, 0L, 0L)
    }
})
