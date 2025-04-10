package io.github.crow_misia.aws.iot.publisher

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.publish
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class FlowMqttMessageQueueTest : StringSpec({
    "メッセージの受信ができること" {
        val counter = AtomicInteger()
        val sut = MqttMessageQueue.wrap {
            while (true) {
                emit(DummyMqttMessage(counter.incrementAndGet()))
            }
        }
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results), any()) }

        sut.asFlow(client)
            .take(10)
            .launchIn(this)
            .join()

        results.map { ByteBuffer.wrap(it.data).getInt() } shouldBe (1..10).map { it }
    }

    "送信途中にエラーが発生した場合、フローが中断しないこと" {
        val counter = AtomicInteger()
        val sut = MqttMessageQueue.wrap {
            while (true) {
                when (val count = counter.incrementAndGet()) {
                    5 -> error("send error!")
                    else -> emit(DummyMqttMessage(count))
                }
            }
        }
        val results = mutableListOf<MqttMessage>()
        // for AWSIotMqttManagerExt mocking
        mockkStatic("io.github.crow_misia.aws.iot.AWSIotMqttManagerExtKt")
        val client = mockk<AWSIotMqttManager>()
        coJustRun { client.publish(capture(results), any()) }

        sut.asFlow(client)
            .retry()
            .take(9)
            .launchIn(this)
            .join()

        results.map { ByteBuffer.wrap(it.data).getInt() } shouldBe listOf(1, 2, 3, 4, 6, 7, 8, 9, 10)
    }
})
