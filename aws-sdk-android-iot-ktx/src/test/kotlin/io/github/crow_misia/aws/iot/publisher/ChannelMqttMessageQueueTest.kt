package io.github.crow_misia.aws.iot.publisher

import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.Instant
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.AWSIoTMqttDeliveryException
import io.github.crow_misia.aws.iot.publish
import io.kotest.assertions.asClue
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChannelMqttMessageQueueTest : StringSpec({
    "メッセージの受信ができること" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L)
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
            .launchIn(this)

        async {
            for (i in 0 until 1000) {
                sut.send(
                    DummyMqttMessage(i * 2),
                    DummyMqttMessage(i * 2 + 1),
                )
            }
        }.await()

        val expected = (0 until 2000).toList()
        eventually(5.seconds) {
            val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
            calledPublishMessages.asClue { it shouldBe expected }
        }

        job.cancelAndJoin()
    }

    "エラー発生時のメッセージがリトライ時に再送されること" {
        val clockMock = mockk<Clock>()
        // when send 1, 2 send 2 retry, send 3
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886600L)
        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 10.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun {
            client.publish(capture(results), any())
        } andThenThrows AWSIoTMqttDeliveryException("error") andThenJust Runs andThenJust Runs

        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 1.minutes)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send(DummyMqttMessage(1))
            // リトライ時に、上のonEach処理の実行が中断される場合があるため、空になるまで待機する
            sut.awaitUntilEmpty(1.seconds)
            sut.send(DummyMqttMessage(2)) // 1st error
            sut.send(DummyMqttMessage(3))
        }.await()

        eventually(5.seconds) {
            val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
            calledPublishMessages.asClue { it shouldBe listOf(1, 2, 3, 2) }
            publishSuccessList.asClue { it shouldBe listOf(1, 3, 2) }
        }

        job.cancelAndJoin()
    }

    "期限切れデータは送信されないこと" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L)

        val sut = MqttMessageQueue.createMessageQueue(
            clock = clockMock,
            messageExpired = 1.minutes,
            pollInterval = 10.milliseconds,
        )
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun {
            client.publish(capture(results), any())
        }
        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 1.minutes)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send(MqttQueueMessage.wrap(DummyMqttMessage(1)) { Instant.fromEpochSeconds(0) })
        }.await()

        continually(5.seconds) {
            results shouldHaveSize 0
            publishSuccessList shouldHaveSize 0
        }

        job.cancelAndJoin()
    }

    "クローズ前に送信したメッセージが送信し終わるまで、クローズが待機されること" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L)
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
        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send((1..count).map {
                DummyMqttMessage(it)
            })
        }.await()

        // クローズする
        sut.awaitUntilEmpty(2.seconds)
        job.cancelAndJoin()

        val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
        calledPublishMessages.asClue { it shouldBe (1..count).toList() }
        publishSuccessList.asClue { it shouldBe (1..count).toList() }
    }

    "クローズ前に送信したメッセージ送信に時間がかかり、クローズのタイムアウトとなる場合、途中で終了すること" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L)
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
        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send((1..count).map {
                DummyMqttMessage(it)
            })
        }.await()

        // 3つ目のメッセージ送信中にタイムアウトする
        val awaitResult = sut.awaitUntilEmpty(2500.milliseconds)

        val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
        calledPublishMessages.asClue { it shouldBe listOf(1, 2, 3) }
        publishSuccessList.asClue { it shouldBe listOf(1, 2) }
        awaitResult shouldBe false

        job.cancelAndJoin()
    }

    "クライアントに紐づける前に送信されたデータが欠損しないこと" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L)
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

        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send(
                DummyMqttMessage(1),
                DummyMqttMessage(2),
            )
        }.await()

        eventually(5.seconds) {
            val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
            calledPublishMessages.asClue { it shouldBe listOf(99, 1, 2) }
            publishSuccessList.asClue { it shouldBe listOf(99, 1, 2) }
        }

        job.cancelAndJoin()
    }

    "クローズ前に送信したメッセージが有効期限切れの場合、空判定がタイムアウトしないこと" {
        val clockMock = mockk<Clock>()
        every { clockMock.now() } returns Instant.fromEpochSeconds(1706886000L) andThen
                Instant.fromEpochSeconds(1706886600L)
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

        val publishSuccessList = CopyOnWriteArrayList<Int>()
        val job = sut.asFlow(client, 100.milliseconds)
            .retry()
            .onEach { publishSuccessList.add(ByteBuffer.wrap(it.data).getInt()) }
            .launchIn(this)

        async {
            sut.send(DummyMqttMessage(1))
        }.await()

        // クローズする
        val awaitResult = sut.awaitUntilEmpty(2500.milliseconds)

        val calledPublishMessages = results.map { ByteBuffer.wrap(it.data).getInt() }
        listOf(calledPublishMessages, publishSuccessList).asClue {
            calledPublishMessages.asClue { it shouldHaveSize 0 }
            publishSuccessList.asClue { it shouldHaveSize 0 }
            awaitResult shouldBe true
        }

        job.cancelAndJoin()
    }
})
