//  Copyright (Change Date see Readme), gematik GmbH
//
//  Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
//  European Commission – subsequent versions of the EUPL (the "Licence").
//  You may not use this work except in compliance with the Licence.
//
//  You find a copy of the Licence in the "Licence" file or at
//  https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the Licence is distributed on an "AS IS" basis,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
//  In case of changes by gematik find details in the "Readme" file.
//
//  See the Licence for the specific language governing permissions and limitations under the Licence.
//
//  *******
//
// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
//

package de.gematik.ti.pushgateway.service

import arrow.core.left
import arrow.core.right
import com.eatthepath.pushy.apns.ApnsClient
import com.eatthepath.pushy.apns.PushNotificationResponse
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture
import de.gematik.ti.pushgateway.extension.pushClientInfo
import de.gematik.ti.pushgateway.failure.ApnsNotificationRejected
import de.gematik.ti.pushgateway.failure.CouldNotBuildApnsPushNotificationPayload
import de.gematik.ti.pushgateway.failure.CouldNotFindApnsClient
import de.gematik.ti.pushgateway.failure.CouldNotSendApnsNotification
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.openapi.model.Device
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.slf4j.Logger

class ApplePushNotificationServiceTest :
  ShouldSpec({
    val pushClientProvider = mockk<PushClientProvider>()
    val logger: Logger = mockk<Logger>(relaxed = true)

    val throwingApnsClient =
      mockk<ApnsClient>() {
        every { sendNotification(any()) } throws RuntimeException("something went wrong")
      }

    val acceptingApnsClient =
      mockk<ApnsClient>() {
        val responseMock =
          mockk<
            PushNotificationFuture<
              SimpleApnsPushNotification,
              PushNotificationResponse<SimpleApnsPushNotification>,
            >
          >(
            relaxed = true
          ) {
            every { toCompletableFuture() } returns
              mockk() {
                every { isDone } returns true
                every { get() } returns
                  mockk<PushNotificationResponse<SimpleApnsPushNotification>>() {
                    every { isAccepted } returns true
                    every { rejectionReason } returns null
                  }
              }
          }
        every { sendNotification(any<SimpleApnsPushNotification>()) } returns responseMock
      }

    val rejectingApnsClient =
      mockk<ApnsClient>() {
        val responseMock =
          mockk<
            PushNotificationFuture<
              SimpleApnsPushNotification,
              PushNotificationResponse<SimpleApnsPushNotification>,
            >
          >(
            relaxed = true
          ) {
            every { toCompletableFuture() } returns
              mockk() {
                every { isDone } returns true
                every { get() } returns
                  mockk<PushNotificationResponse<SimpleApnsPushNotification>>() {
                    every { isAccepted } returns false
                    every { rejectionReason } returns Optional.of("rejected")
                  }
              }
          }
        every { sendNotification(any<SimpleApnsPushNotification>()) } returns responseMock
      }

    val sut = ApplePushNotificationService(logger = logger, pushClientProvider = pushClientProvider)

    val pushKey1 = "valid.pushKey.1"
    val pushKey2 = "valid.pushKey.2"
    val pushClientIdentifier = PushClientIdentifier(value = "valid.bundleId")
    val device1 = Device(appId = "${pushClientIdentifier.value}.apns", pushkey = pushKey1)
    val device2 = Device(appId = "${pushClientIdentifier.value}.apns", pushkey = pushKey2)

    val pushClientInfo = device1.pushClientInfo.getOrNull()!!

    beforeTest { clearMocks(pushClientProvider, logger) }

    context("plain") {
      withData(true, false) { isDebugEnabled ->
        should("handle APNs response accepted") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            acceptingApnsClient.right()

          sut.sendNotification(
            pushClientInfo = pushClientInfo,
            notification =
              PlainNotification(
                devices = setOf(device1, device2),
                eventId = "eventId",
                roomId = "roomId",
              ),
          ) shouldBeRight Unit
        }

        should("deny incomplete payload") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            rejectingApnsClient.right()

          sut
            .sendNotification(
              pushClientInfo = pushClientInfo,
              notification =
                PlainNotification(devices = setOf(device1, device2), eventId = "eventId"),
            )
            .shouldBeLeft()
            .apply {
              shouldBeInstanceOf<CouldNotBuildApnsPushNotificationPayload>()
              pushKey shouldBe pushClientInfo.pushKey
            }
        }

        should("handle APNs response rejected") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            rejectingApnsClient.right()

          sut
            .sendNotification(
              pushClientInfo = pushClientInfo,
              notification =
                PlainNotification(
                  devices = setOf(device1, device2),
                  eventId = "eventId",
                  roomId = "roomId",
                ),
            )
            .shouldBeLeft()
            .apply {
              shouldBeInstanceOf<ApnsNotificationRejected>()
              pushKey shouldBe pushClientInfo.pushKey
              rejectionReason shouldBe "rejected"
            }
        }
      }
    }

    context("encrypted") {
      withData(true, false) { isDebugEnabled ->
        should("handle APNs response accepted") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            acceptingApnsClient.right()

          sut.sendNotification(
            pushClientInfo = pushClientInfo,
            notification =
              EncryptedNotification(
                ciphertext = "ciphertext",
                timeMessageEncrypted = "timeMessageEncrypted",
                keyIdentifier = "kid",
                device = device1,
              ),
          ) shouldBeRight Unit
        }

        should("handle APNs response rejected") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            rejectingApnsClient.right()

          sut
            .sendNotification(
              pushClientInfo = pushClientInfo,
              notification =
                EncryptedNotification(
                  ciphertext = "ciphertext",
                  timeMessageEncrypted = "timeMessageEncrypted",
                  keyIdentifier = "kid",
                  device = device1,
                ),
            )
            .shouldBeLeft()
            .apply {
              shouldBeInstanceOf<ApnsNotificationRejected>()
              pushKey shouldBe pushClientInfo.pushKey
              rejectionReason shouldBe "rejected"
            }
        }

        should("not be able to send without push client") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            false
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            CouldNotFindApnsClient(bundleId = pushClientInfo.pushClientIdentifier).left()

          sut
            .sendNotification(
              pushClientInfo = pushClientInfo,
              notification =
                EncryptedNotification(
                  ciphertext = "ciphertext",
                  timeMessageEncrypted = "timeMessageEncrypted",
                  keyIdentifier = "kid",
                  device = device1,
                ),
            )
            .shouldBeLeft()
            .apply {
              shouldBeInstanceOf<CouldNotFindApnsClient>()
              bundleId shouldBe pushClientInfo.pushClientIdentifier
            }
        }

        should("handle apns client exception") {
          every { logger.isDebugEnabled } returns isDebugEnabled
          every { pushClientProvider.hasApnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            true
          every { pushClientProvider.apnsClientFor(pushClientInfo.pushClientIdentifier) } returns
            throwingApnsClient.right()

          sut
            .sendNotification(
              pushClientInfo = pushClientInfo,
              notification =
                EncryptedNotification(
                  ciphertext = "ciphertext",
                  timeMessageEncrypted = "timeMessageEncrypted",
                  keyIdentifier = "kid",
                  device = device1,
                ),
            )
            .shouldBeLeft {
              shouldBeInstanceOf<CouldNotSendApnsNotification>()
              pushKey shouldBe pushClientInfo.pushKey
            }
        }
      }
    }
  })
