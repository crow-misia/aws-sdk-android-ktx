package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.AWSIoTMqttDeliveryException
import io.github.crow_misia.aws.iot.publish
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration.Companion.minutes

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

        launch {
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

        launch {
            sut.send(DummyMqttMessage(1))
            sut.send(DummyMqttMessage(2))
            sut.send(DummyMqttMessage(3))
        }
        job.join()

        results.map { it.data[0].toInt() } shouldBe listOf(1, 2, 3, 3)
        publishSuccessList shouldBe listOf(1, 3)
    }
})
