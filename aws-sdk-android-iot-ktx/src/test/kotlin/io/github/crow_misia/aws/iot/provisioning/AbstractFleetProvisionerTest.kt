package io.github.crow_misia.aws.iot.provisioning

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import io.github.crow_misia.aws.iot.AWSIoTMqttPublishWithReplyException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningException
import io.github.crow_misia.aws.iot.AWSIoTProvisioningResponse
import io.github.crow_misia.aws.iot.model.ProvisioningErrorResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.internal.ExceptionHelper
import kotlin.time.Duration.Companion.milliseconds

@ExperimentalSerializationApi
class AbstractFleetProvisionerTest : StringSpec({
    val mqttManager: AWSIotMqttManager = mockk()

    // dummy privateKey for test
    val privateKeyPem = """
        -----BEGIN RSA PRIVATE KEY-----
        MIICXQIBAAKBgQCu4n1yuIxg0l+ydqBIs14nlrvwhBhP1pIWRmKLFttGL/isjWeA
        4P/MPk+CKc1J115vRikk5jhYx15WdjJh73wkCjeo0QkQ7eEnMCX0qy189E4O01YV
        F/4d5xQjoQX96LCDK7R4oCzaW5higvT14ftY9s687aHdC2Ks48FX9BlaPQIDAQAB
        AoGAGqvqK/PfXOjYtXQID+5jHrCLaywKNSrpTsJfRw0uVe79Avvx3CL0gCbEo3pS
        l79j5J6Tqc/+qfOF/LO8DVmj2e62M2XDdtgh19ScdjA/9W4h8HgZHeE+8RUDOc0p
        K812bprndol1mde1LMbOG5AVBh/32TOVGIHm1wEcOrNKxWECQQDVU6Io3POtWMaT
        3e7ThjNPPQ4/4/GHYlj1QTJCV5O+vuECb9NZ1BbJwvQ4C6pxHo7Bo+CE2KYNdG1Y
        rN/j3FKpAkEA0d5CpJ2dWiC/uYyeefvRcQmPIsklbOIX+4X7K25H+umqhym3GnFe
        v++yeXPm2sGijyvoIQLkfj3um6vLV8nbdQJAbkWGHGNinue19neRXcwQN9SSyhn1
        HwuKenSSG1MT71TSESVm9hc5FGBvR41YNJMcLiKdC27GH0xgvabXMP0RyQJBAIwq
        0QHXDzAR7VtE8tdv+4tuof3OsENPokroSj/QkRyhEbbigpuTmn/A3MfHRClY6HSN
        E6VwpCE4xfOMwuvoMr0CQQC4wfrB4JdI+lpzipZe+bgE1LsiEepbTX2vQ1MwZ68B
        pGP8pbvZ8Ub3IDEBmUEqklobvppYbqe8L7kGm/7S7vtZ
        -----END RSA PRIVATE KEY-----
    """.trimIndent()

    val sut = object : AbstractFleetProvisioner(
        mqttManager = mqttManager,
    ) {
        override suspend fun process(
            templateName: String,
            parameters: Map<String, String>
        ): AWSIoTProvisioningResponse {
            return AWSIoTProvisioningResponse(
                deviceConfiguration = emptyMap(),
                thingName = "testThing",
                certificateId = "certId",
                certificatePem = "certPem",
                privateKeyPem = privateKeyPem,
            )
        }
    }

    "プロビジョニングが行えること" {
        val result = sut.provisioningThing(templateName = "test") {
            flowOf(AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected)
        }
        result shouldNotBe null
    }

    "応答がない場合にタイムアウトすること" {
        shouldThrow<TimeoutCancellationException> {
            sut.provisioningThing(templateName = "test", timeout = 100.milliseconds) {
                channelFlow { awaitClose() }
            }
        }
    }

    "指定タイムアウト前に、接続タイムアウトが発生" {
        shouldThrow<MqttException> {
            sut.provisioningThing(templateName = "test", timeout = 1000.milliseconds) {
                channelFlow {
                    close(ExceptionHelper.createMqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt()))
                }
            }
        }
    }

    "プロビジョニング中に、予期しないエラーが発生" {
        shouldThrow<IllegalStateException> {
            sut.provisioningThing(templateName = "test", timeout = 1000.milliseconds) {
                flow {
                    error("error")
                }
            }
        }
    }

    "プロビジョニング中に、IoT Coreからエラーレスポンス" {
        shouldThrowWithMessage<AWSIoTProvisioningException>(
            message = "message",
        ) {
            sut.provisioningThing(templateName = "test", timeout = 1000.milliseconds) {
                flow {
                    val response = ProvisioningErrorResponse(statusCode = 1, errorCode = "code", errorMessage = "message")
                    val data = Cbor.encodeToByteArray(response)
                    throw AWSIoTMqttPublishWithReplyException(
                        message = "Accept",
                        topic = "topic",
                        response = data,
                    )
                }
            }
        }
    }

    "プロビジョニング中に、IoT Coreからエラーレスポンスがデコード出来ない" {
        shouldThrowWithMessage<SerializationException>(
            message = "Expected start of map, but found 00",
        ) {
            sut.provisioningThing(templateName = "test", timeout = 1000.milliseconds) {
                flow {
                    throw AWSIoTMqttPublishWithReplyException(
                        message = "Accept",
                        topic = "topic",
                        response = byteArrayOf(0x00, 0x12, 0x34),
                        userData = null,
                    )
                }
            }
        }
    }
})
