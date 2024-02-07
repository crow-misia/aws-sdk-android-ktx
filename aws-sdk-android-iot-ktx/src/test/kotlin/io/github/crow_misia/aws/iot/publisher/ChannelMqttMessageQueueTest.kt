package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.AWSIoTMqttDeliveryException
import io.github.crow_misia.aws.iot.publish
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAll
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
            messageExpired = 1.minutes,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results)) }

        val job = sut.asFlow(client)
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
        // when send 2 error, retry, send 3 error, retry
        every { clockMock.millis() } returns 1706886000000L andThen 1706886600000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results)) } andThenThrowsMany(listOf(AWSIoTMqttDeliveryException("error"), AWSIoTMqttDeliveryException("error"))) andThenJust Runs

        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client)
            .retry()
            .take(2)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            sut.send(DummyMqttMessage(1))
            sut.send(DummyMqttMessage(2))
            sut.send(DummyMqttMessage(3))
        }
        job.join()

        results.map { it.data[0].toInt() } shouldBe listOf(1, 2, 3, 3)
        publishSuccessList shouldBe listOf(1, 3)
    }

    "クローズ前に送信したメッセージが送信し終わるで、クローズが待機されること" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coEvery { client.publish(capture(results)) } coAnswers {
            delay(300.milliseconds)
        }

        val count = 5
        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client)
            .retry()
            .take(count)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            (1..count).forEach {
                sut.send(DummyMqttMessage(it))
            }
            sut.close(2.seconds)
        }
        job.cancelAndJoin()

        results.map { it.data[0].toInt() } shouldBe (1..count).toList()
        publishSuccessList shouldBe (1..count).toList()
    }

    "クローズ前に送信したメッセージ送信に時間がかかり、クローズのタイムアウトとなる場合、途中で終了すること" {
        val clockMock = mockk<Clock>()
        every { clockMock.millis() } returns 1706886000000L
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coEvery { client.publish(capture(results)) } coAnswers {
            delay(1.seconds)
        }

        val count = 10
        val publishSuccessList = mutableListOf<Int>()
        val job = sut.asFlow(client)
            .retry()
            .take(count)
            .onEach { publishSuccessList.add(it.data[0].toInt()) }
            .launchIn(this)

        withContext(Dispatchers.Default) {
            (1..count).forEach {
                sut.send(DummyMqttMessage(it))
            }
            sut.close(1500.milliseconds)
        }
        job.cancelAndJoin()

        results.map { it.data[0].toInt() } shouldNotContainAll (4..count).toList()
        publishSuccessList shouldNotContainAll (4..count).toList()
    }
})
