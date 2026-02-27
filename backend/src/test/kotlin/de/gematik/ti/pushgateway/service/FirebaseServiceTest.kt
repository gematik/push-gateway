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
import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.failure.CouldNotBuildFirebasePushNotification
import de.gematik.ti.pushgateway.failure.CouldNotFindFirebaseClient
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.Device
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.slf4j.Logger

class FirebaseServiceTest :
  ShouldSpec({
    val pushClientProvider = mockk<PushClientProvider>()
    val sendService = mockk<InstrumentedFirebaseSendService>()

    val logger = mockk<Logger>(relaxed = true)

    val sut =
      FirebaseService(
        logger = logger,
        pushClientProvider = pushClientProvider,
        sendService = sendService,
      )

    beforeTest { clearAllMocks() }

    val pushKey1 = "valid.pushKey.1"
    val pushKey2 = "valid.pushKey.2"
    val pushClientIdentifier = PushClientIdentifier(value = "valid-project-id")
    val device1 = Device(appId = "${pushClientIdentifier.value}.firebase", pushkey = pushKey1)
    val device2 = Device(appId = "${pushClientIdentifier.value}.firebase", pushkey = pushKey2)

    context("plain") {
      should("send notification to firebase") {
        this@context.withData(first = true, second = false) { isDebugEnabled ->
          every { pushClientProvider.hasFirebaseClientFor(pushClientIdentifier) } returns true
          every { pushClientProvider.firebaseClientFor(pushClientIdentifier) } returns
            mockk<FirebaseMessaging>(relaxed = true).right()
          every { sendService.sendMessage(any(), any(), any()) } returns
            FirebaseMessageId(value = "some-message-id").right()
          every { logger.isDebugEnabled } returns isDebugEnabled

          sut.sendNotification(
            pushClientInfo =
              PushClientInfo(
                appId = "valid-project-id.firebase",
                pushKey = "valid.pushKey",
                pushClientIdentifier = pushClientIdentifier,
                pushProvider = PushProvider.ANDROID,
              ),
            notification =
              PlainNotification(
                devices = setOf(device1, device2),
                eventId = "eventId",
                roomId = "roomId",
              ),
          ) shouldBeRight FirebaseMessageId(value = "some-message-id")
        }
      }

      should("not send incomplete notification to firebase") {
        every { pushClientProvider.hasFirebaseClientFor(pushClientIdentifier) } returns true
        every { pushClientProvider.firebaseClientFor(pushClientIdentifier) } returns
          mockk<FirebaseMessaging>(relaxed = true).right()
        every { sendService.sendMessage(any(), any(), any()) } returns
          FirebaseMessageId(value = "some-message-id").right()

        sut.sendNotification(
          pushClientInfo =
            PushClientInfo(
              appId = "valid-project-id.firebase",
              pushKey = "valid.pushKey",
              pushClientIdentifier = pushClientIdentifier,
              pushProvider = PushProvider.ANDROID,
            ),
          notification = PlainNotification(devices = setOf(device1, device2), eventId = "eventId"),
        ) shouldBeLeft
          CouldNotBuildFirebasePushNotification(
            token = "valid.pushKey",
            details = "roomId must not be null",
          )
      }
    }

    context("encrypted") {
      should("send notification to firebase") {
        this@context.withData(true, second = false) { isDebugEnabled ->
          every { pushClientProvider.hasFirebaseClientFor(pushClientIdentifier) } returns true
          every { pushClientProvider.firebaseClientFor(pushClientIdentifier) } returns
            mockk<FirebaseMessaging>(relaxed = true).right()
          every { sendService.sendMessage(any(), any(), any()) } returns
            FirebaseMessageId(value = "some-message-id").right()
          every { logger.isDebugEnabled } returns isDebugEnabled

          sut.sendNotification(
            pushClientInfo =
              PushClientInfo(
                appId = "valid-project-id.firebase",
                pushKey = "valid.pushKey",
                pushClientIdentifier = pushClientIdentifier,
                pushProvider = PushProvider.ANDROID,
              ),
            notification =
              EncryptedNotification(
                ciphertext = "ciphertext",
                timeMessageEncrypted = "timeMessageEncrypted",
                keyIdentifier = "kid",
                device = device1,
              ),
          ) shouldBeRight FirebaseMessageId(value = "some-message-id")
        }
      }

      should("not be able to send notification to firebase without client") {
        every { pushClientProvider.hasFirebaseClientFor(pushClientIdentifier) } returns true
        every { pushClientProvider.firebaseClientFor(pushClientIdentifier) } returns
          CouldNotFindFirebaseClient(projectId = pushClientIdentifier).left()

        sut.sendNotification(
          pushClientInfo =
            PushClientInfo(
              appId = "valid-project-id.firebase",
              pushKey = "valid.pushKey",
              pushClientIdentifier = pushClientIdentifier,
              pushProvider = PushProvider.ANDROID,
            ),
          notification =
            EncryptedNotification(
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = device1,
            ),
        ) shouldBeLeft CouldNotFindFirebaseClient(projectId = pushClientIdentifier)
      }
    }
  })
