package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.AWSIoTMqttDeliveryException
import io.github.crow_misia.aws.iot.publish
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChannelMqttMessageQueueTest : StringSpec({
    "メッセージの受信ができること" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 100.minutes,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results), any()) }

        val job = sut.asFlow(client, 100.milliseconds)
            .take(2)
            .launchIn(this)

        withContext(Dispatchers.Default) {
            sut.send(DummyMqttMessage(1))
            sut.send(DummyMqttMessage(2))
        }
        job.join()

        results.map { it.data[0].toInt() } shouldBe (1..2).map { it }
    }

    "エラー発生時のメッセージがリトライ時に再送されること" {
        val clockMock = mockk<Clock>()
        // 2番目の送信データは期限切れで再送されないこと
        // when send 1, 2, 3, send 3 retry
        every { clockMock.millis() } returnsMany listOf(
            1706886600000L, 1706886000000L, 1706886600000L, 1706886600000L,
        )
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 100.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun {
            client.publish(capture(results), any())
        } andThenThrows AWSIoTMqttDeliveryException("error") andThenJust Runs

        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .take(2)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            sut.send(DummyMqttMessage(1))
            sut.send(DummyMqttMessage(2)) // skip
            sut.send(DummyMqttMessage(3)) // 1st error
        }
        job.join()

        results.map { it.data[0].toInt() } shouldBe listOf(1, 3, 3)
        publishSuccessList shouldBe listOf(1, 3)
    }

    "クローズ前に送信したメッセージが送信し終わるで、クローズが待機されること" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 100.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coEvery { client.publish(capture(results), any()) } coAnswers {
            delay(300.milliseconds)
        }

        val count = 5
        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .take(count)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            (1..count).forEach {
                sut.send(DummyMqttMessage(it))
            }
            sut.awaitUntilEmpty(2.seconds)
        }
        job.cancelAndJoin()

        val calledPublishMessages = results.map { it.data[0].toInt() }
        println(calledPublishMessages)
        calledPublishMessages shouldBe (1..count).toList()
        publishSuccessList shouldBe (1..count).toList()
    }

    "クローズ前に送信したメッセージ送信に時間がかかり、クローズのタイムアウトとなる場合、途中で終了すること" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 100.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coEvery { client.publish(capture(results), any()) } coAnswers {
            delay(1.seconds)
        }

        val count = 10
        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .take(count)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            (1..count).forEach {
                sut.send(DummyMqttMessage(it))
            }
            // 3つ目のメッセージ送信中にタイムアウトする
            sut.awaitUntilEmpty(2500.milliseconds)
        }
        job.cancelAndJoin()

        val calledPublishMessages = results.map { it.data[0].toInt() }
        println(calledPublishMessages)
        calledPublishMessages shouldBe (1..3).toList()
        publishSuccessList shouldBe (1..2).toList()
    }

    "クライアントに紐づける前に送信されたデータが欠損しないこと" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 100.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results), any()) }

        sut.send(DummyMqttMessage(99))

        val count = 2
        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .take(3)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            (1..count).forEach {
                sut.send(DummyMqttMessage(it))
            }
            sut.awaitUntilEmpty(100.milliseconds)
        }
        job.cancelAndJoin()

        results.map { it.data[0].toInt() } shouldBe listOf(99, 1, 2)
        publishSuccessList shouldBe listOf(99, 1, 2)
    }
})
