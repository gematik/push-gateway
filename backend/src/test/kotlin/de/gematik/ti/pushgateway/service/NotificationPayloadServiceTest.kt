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
import de.gematik.ti.pushgateway.failure.CouldNotFindApnsClient
import de.gematik.ti.pushgateway.failure.CouldNotFindFirebaseClient
import de.gematik.ti.pushgateway.failure.CouldNotFindPushClient
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.*
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

class NotificationPayloadServiceTest :
  ShouldSpec({
    val rejectedPushKeyService: RejectedPushKeyService = mockk(relaxed = true)
    val pushClientProvider: PushClientProvider = mockk(relaxed = true)

    val sut =
      NotificationPayloadService(
        rejectedPushKeyService = rejectedPushKeyService,
        pushClientProvider = pushClientProvider,
      )

    beforeTest { clearAllMocks() }

    val pushKey1 = "valid.pushKey.1"
    val pushKey2 = "valid.pushKey.2"
    withData(Pair("apns", "valid.bundleId"), Pair("firebase", "valid-project-id")) {
      pushProviderAndIdentifier ->
      val pushClientIdentifier = PushClientIdentifier(value = pushProviderAndIdentifier.second)
      val device1 =
        Device(
          appId = "${pushClientIdentifier.value}.${pushProviderAndIdentifier.first}",
          pushkey = pushKey1,
        )
      val device2 =
        Device(
          appId = "${pushClientIdentifier.value}.${pushProviderAndIdentifier.first}",
          pushkey = pushKey2,
        )

      context("plain") {
        context("right") {
          val notification =
            PlainNotification(
              devices = setOf(device1, device2),
              eventId = "roomId",
              roomId = "eventId",
            )
          val inner1 =
            BatchNotificationRequestNotificationsInner(id = "id1", notification = notification)
          val request = BatchNotificationRequest(notifications = setOf(inner1))

          should("convert to deliverable notification") {
            every { pushClientProvider.hasApnsClientFor(any()) } returns true
            every { pushClientProvider.hasFirebaseClientFor(any()) } returns true
            every { pushClientProvider.apnsClientFor(any()) } returns mockk(relaxed = true)
            every { pushClientProvider.firebaseClientFor(any()) } returns mockk(relaxed = true)

            sut.validateForDelivery(request = request) shouldBe
              mapOf("id1" to Pair(emptyList(), notification))
          }
        }

        context("left") {
          val notification =
            PlainNotification(devices = setOf(device1, device2), eventId = "roomId")
          val inner1 =
            BatchNotificationRequestNotificationsInner(id = "id1", notification = notification)
          val request = BatchNotificationRequest(notifications = setOf(inner1))

          should("convert remove devices without push client") {
            every { pushClientProvider.hasApnsClientFor(any()) } returns false
            every { pushClientProvider.hasFirebaseClientFor(any()) } returns false
            every { pushClientProvider.apnsClientFor(any()) } returns
              CouldNotFindApnsClient(bundleId = pushClientIdentifier).left()
            every { pushClientProvider.firebaseClientFor(any()) } returns
              CouldNotFindFirebaseClient(projectId = pushClientIdentifier).left()

            sut.validateForDelivery(request = request) shouldBe
              mapOf(
                "id1" to
                  Pair(
                    first =
                      listOf(
                        CouldNotFindPushClient(
                          device = device1,
                          pushClientInfo =
                            PushClientInfo(
                              appId = device1.appId,
                              pushKey = device1.pushkey,
                              pushClientIdentifier = pushClientIdentifier,
                              pushProvider = PushProvider.from(device1).getOrNull()!!,
                            ),
                        ),
                        CouldNotFindPushClient(
                          device = device2,
                          pushClientInfo =
                            PushClientInfo(
                              appId = device2.appId,
                              pushKey = device2.pushkey,
                              pushClientIdentifier = pushClientIdentifier,
                              pushProvider = PushProvider.from(device2).getOrNull()!!,
                            ),
                        ),
                      ),
                    second = null,
                  )
              )
          }
        }
      }

      context("encrypted") {
        val notification1 =
          EncryptedNotification(
            ciphertext = "ciphertext",
            timeMessageEncrypted = "timeMessageEncrypted",
            keyIdentifier = "kid",
            device = device1,
          )
        val notification2 =
          EncryptedNotification(
            ciphertext = "ciphertext",
            timeMessageEncrypted = "timeMessageEncrypted",
            keyIdentifier = "kid",
            device = device2,
          )
        val batchItem1 =
          EncryptedBatchNotificationRequestNotificationsInner(
            id = "id1",
            notification = notification1,
          )
        val batchItem2 =
          EncryptedBatchNotificationRequestNotificationsInner(
            id = "id2",
            notification = notification2,
          )
        val request =
          EncryptedBatchNotificationRequest(notifications = setOf(batchItem1, batchItem2))
        context("right") {
          should("convert to deliverable notification") {
            every { pushClientProvider.hasApnsClientFor(any()) } returns true
            every { pushClientProvider.hasFirebaseClientFor(any()) } returns true
            every { pushClientProvider.apnsClientFor(any()) } returns mockk(relaxed = true)
            every { pushClientProvider.firebaseClientFor(any()) } returns mockk(relaxed = true)

            sut.validateForDelivery(request = request) shouldBe
              mapOf("id1" to notification1.right(), "id2" to notification2.right())
          }
        }

        context("left") {
          every { pushClientProvider.hasApnsClientFor(any()) } returns false
          every { pushClientProvider.hasFirebaseClientFor(any()) } returns false
          every { pushClientProvider.apnsClientFor(any()) } returns
            CouldNotFindApnsClient(bundleId = pushClientIdentifier).left()
          every { pushClientProvider.firebaseClientFor(any()) } returns
            CouldNotFindFirebaseClient(projectId = pushClientIdentifier).left()

          should("resolve to left for devices without push client") {
            sut.validateForDelivery(request = request) shouldBe
              mapOf(
                "id1" to
                  CouldNotFindPushClient(
                      device = device1,
                      pushClientInfo =
                        PushClientInfo(
                          appId = device1.appId,
                          pushKey = device1.pushkey,
                          pushClientIdentifier = pushClientIdentifier,
                          pushProvider = PushProvider.from(device1).getOrNull()!!,
                        ),
                    )
                    .left(),
                "id2" to
                  CouldNotFindPushClient(
                      device = device2,
                      pushClientInfo =
                        PushClientInfo(
                          appId = device2.appId,
                          pushKey = device2.pushkey,
                          pushClientIdentifier = pushClientIdentifier,
                          pushProvider = PushProvider.from(device2).getOrNull()!!,
                        ),
                    )
                    .left(),
              )
          }
        }
      }
    }
  })
