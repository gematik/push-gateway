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

package de.gematik.ti.pushgateway.extension

import com.eatthepath.pushy.apns.DeliveryPriority
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.google.firebase.messaging.Message
import de.gematik.ti.pushgateway.failure.CouldNotBuildApnsPushNotificationPayload
import de.gematik.ti.pushgateway.openapi.model.Device
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.Priority
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.every
import io.mockk.mockk

class EncryptedNotificationExtensionTest :
  ShouldSpec({
    context("Apple Push Notification service") {
      context("priority") {
        should("convert HIGH to IMMEDIATE") {
          val encryptedNotification =
            EncryptedNotification(
              prio = Priority.HIGH,
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = Device(appId = "valid.appId.apns", pushkey = "valid.pushKey"),
            )

          encryptedNotification.deliveryPriority shouldBe DeliveryPriority.IMMEDIATE
        }
        should("convert not specified to IMMEDIATE") {
          val encryptedNotification =
            EncryptedNotification(
              prio = null,
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = Device(appId = "valid.appId.apns", pushkey = "valid.pushKey"),
            )

          encryptedNotification.deliveryPriority shouldBe DeliveryPriority.IMMEDIATE
        }
        should("convert LOW to CONSERVE_POWER") {
          val encryptedNotification =
            EncryptedNotification(
              prio = Priority.LOW,
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = Device(appId = "valid.appId.apns", pushkey = "valid.pushKey"),
            )

          encryptedNotification.deliveryPriority shouldBe DeliveryPriority.CONSERVE_POWER
        }
      }

      context("toApnsPayload") {
        val device = Device(appId = "valid.appId.apns", pushkey = "valis.pushKey")
        val input =
          EncryptedNotification(
            ciphertext = "ciphertext",
            timeMessageEncrypted = "timeMessageEncrypted",
            keyIdentifier = "kid",
            device = device,
          )

        context("right") {
          should("convert with proper input") {
            val expected =
              """
              {"ciphertext":"ciphertext","aps":{"alert":{"subtitle":"subtitle","body":"body","title":"title"},"mutable-content":1},"key_identifier":"kid","time_message_encrypted":"timeMessageEncrypted"}
              """
                .trimIndent()
            input.toApnsPayload() shouldBeRight expected
          }
        }

        context("left") {
          should("not convert with invalid payload") {
            val builder =
              mockk<SimpleApnsPayloadBuilder> {
                every { addCustomProperty(any<String>(), any<Any>()) } throws
                  IllegalArgumentException("Custom property key must not be aps")
              }
            input.toApnsPayload(builder) shouldBeLeft
              CouldNotBuildApnsPushNotificationPayload(
                pushKey = device.pushkey,
                details = "Custom property key must not be aps",
              )
          }
        }
      }

      context("toApnsNotification") {
        val device = Device(appId = "valid.appId.apns", pushkey = "valid.pushKey")
        val input =
          EncryptedNotification(
            ciphertext = "ciphertext",
            timeMessageEncrypted = "timeMessageEncrypted",
            keyIdentifier = "kid",
            device = device,
          )

        context("right") {
          should("convert with proper input") {
            input.toApnsNotification(device.pushClientInfo.getOrNull()!!).shouldBeRight().apply {
              shouldBeInstanceOf<SimpleApnsPushNotification>()
            }
          }
        }
      }
    }

    context("Firebase messaging") {
      val device = Device(appId = "valid.appId.apns", pushkey = "valid.pushKey")

      context("toFirebaseMessage") {
        should("convert to Message.right() with nothing filtered from complete payload") {
          val input =
            EncryptedNotification(
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = device,
            )
          input.toFirebaseMessage().shouldBeRight().apply { shouldBeTypeOf<Message>() }
        }

        should("convert to Message.right() with something filtered from payload") {
          val input =
            EncryptedNotification(
              ciphertext = "ciphertext",
              timeMessageEncrypted = "timeMessageEncrypted",
              keyIdentifier = "kid",
              device = device,
            )
          input.toFirebaseMessage().shouldBeRight().apply { shouldBeTypeOf<Message>() }
        }
      }
    }
  })
