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
import de.gematik.ti.pushgateway.failure.CouldNotBuildFirebasePushNotification
import de.gematik.ti.pushgateway.openapi.model.Device
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import de.gematik.ti.pushgateway.openapi.model.Priority
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.every
import io.mockk.mockk

class PlainNotificationExtensionTest :
  ShouldSpec({
    context("Apple Push Notification service") {
      context("A_27167: priority mapping") {
        should("convert HIGH to IMMEDIATE") {
          val plainNotification = PlainNotification(prio = Priority.HIGH, devices = emptySet())

          plainNotification.apnsPriority shouldBe DeliveryPriority.IMMEDIATE
        }
        should("convert not specified to IMMEDIATE") {
          val plainNotification = PlainNotification(devices = emptySet())

          plainNotification.apnsPriority shouldBe DeliveryPriority.IMMEDIATE
        }
        should("convert LOW to CONSERVE_POWER") {
          val plainNotification = PlainNotification(prio = Priority.LOW, devices = emptySet())

          plainNotification.apnsPriority shouldBe DeliveryPriority.CONSERVE_POWER
        }
      }

      context("toApnsPayload") {
        val device = Device(appId = "valid.appId.apns", pushkey = "valis.pushKey")
        context("left") {
          should("not convert without eventId") {
            val input = PlainNotification(prio = Priority.LOW, devices = setOf(device))
            input.toApnsPayload(device.pushkey) shouldBeLeft
              CouldNotBuildApnsPushNotificationPayload(
                pushKey = device.pushkey,
                details = "eventId must not be null",
              )
          }
          should("not convert with invalid payload") {
            val input =
              PlainNotification(
                prio = Priority.LOW,
                devices = setOf(device),
                eventId = "eventId",
                roomId = "roomId",
              )

            val builder =
              mockk<SimpleApnsPayloadBuilder> {
                every { addCustomProperty(any<String>(), any<Any>()) } throws
                  IllegalArgumentException("Custom property key must not be aps")
              }

            input.toApnsPayload(device.pushkey, builder) shouldBeLeft
              CouldNotBuildApnsPushNotificationPayload(
                pushKey = device.pushkey,
                details = "Custom property key must not be aps",
              )
          }

          should("not convert without roomId") {
            val input = PlainNotification(eventId = "eventId", devices = setOf(device))
            input.toApnsPayload(device.pushkey) shouldBeLeft
              CouldNotBuildApnsPushNotificationPayload(
                pushKey = device.pushkey,
                details = "roomId must not be null",
              )
          }
        }

        context("right") {
          should("convert with proper input") {
            val input =
              PlainNotification(eventId = "eventId", roomId = "roomId", devices = setOf(device))
            val expected =
              """
              {"room_id":"roomId","event_id":"eventId","aps":{"alert":{"subtitle":"subtitle","body":"body","title":"title"},"mutable-content":1}}
              """
                .trimIndent()
            input.toApnsPayload(device.pushkey) shouldBeRight expected
          }
          should("convert with proper input and custom attributes") {
            val input =
              PlainNotification(
                eventId = "eventId",
                roomId = "roomId",
                devices = setOf(device),
                type = "type",
                sender = "sender",
                senderDisplayName = "senderDisplayName",
                roomName = "roomName",
                roomAlias = "roomAlias",
              )

            input.toApnsPayload(device.pushkey).shouldBeRight().apply {
              shouldBeInstanceOf<String>()
              shouldContain(""""room_id":"roomId"""")
              shouldContain(""""room_name":"roomName"""")
              shouldContain(""""event_id":"eventId"""")
              shouldContain(""""aps":{""")
              shouldContain(""""alert":{""")
              shouldContain(""""subtitle":"subtitle"""")
              shouldContain(""""body":"body"""")
              shouldContain(""""title":"title"""")
              shouldContain(""""mutable-content":1""")
              shouldContain("""}""")
              shouldContain(""""sender":"sender"""")
              shouldContain(""""sender_display_name":"senderDisplayName"""")
              shouldContain(""""type":"type"""")
            }
          }
        }
      }

      context("toApnsNotification") {
        val device = Device(appId = "valid.appId.apns", pushkey = "valis.pushKey")
        context("left") {
          should("not convert without eventId") {
            val input = PlainNotification(devices = setOf(device))
            input.toApnsNotification(device.pushClientInfo.getOrNull()!!) shouldBeLeft
              CouldNotBuildApnsPushNotificationPayload(
                pushKey = device.pushkey,
                details = "eventId must not be null",
              )
          }
        }
        context("right") {
          should("convert with proper input") {
            val input =
              PlainNotification(eventId = "eventId", roomId = "roomId", devices = setOf(device))
            input.toApnsNotification(device.pushClientInfo.getOrNull()!!).shouldBeRight().apply {
              shouldBeInstanceOf<SimpleApnsPushNotification>()
            }
          }
        }
      }
    }

    context("Firebase messaging") {
      context("toFirebaseMessage") {
        should("convert to Message.right() with nothing filtered from complete payload") {
          val plainNotification =
            PlainNotification(
              prio = Priority.HIGH,
              devices = setOf(Device(appId = "project-id.app.id.firebase", pushkey = "push.key")),
              eventId = "eventId",
              roomId = "roomId",
              type = "type",
              sender = "sender",
              senderDisplayName = "senderDisplayName",
              roomName = "roomName",
              roomAlias = "roomAlias",
              userIsTarget = true,
            )
          plainNotification.toFirebaseMessage("push.key").shouldBeRight().apply {
            shouldBeTypeOf<Message>()
          }
        }

        should("convert to Message.right() with something filtered from payload") {
          val plainNotification =
            PlainNotification(
              prio = Priority.HIGH,
              devices = setOf(Device(appId = "project-id.app.id.firebase", pushkey = "push.key")),
              eventId = "eventId",
              roomId = "roomId",
            )
          plainNotification.toFirebaseMessage("push.key").shouldBeRight().apply {
            shouldBeTypeOf<Message>()
          }
        }
        should("recognize missing eventId in payload") {
          val plainNotification =
            PlainNotification(
              prio = Priority.HIGH,
              devices = setOf(Device(appId = "project-id.app.id.firebase", pushkey = "push.key")),
              roomId = "roomId",
            )
          plainNotification.toFirebaseMessage("push.key") shouldBeLeft
            CouldNotBuildFirebasePushNotification(
              token = "push.key",
              details = "eventId must not be null",
            )
        }
        should("recognize missing roomId in payload") {
          val plainNotification =
            PlainNotification(
              prio = Priority.HIGH,
              devices = setOf(Device(appId = "project-id.app.id.firebase", pushkey = "push.key")),
              eventId = "eventId",
            )
          plainNotification.toFirebaseMessage("push.key") shouldBeLeft
            CouldNotBuildFirebasePushNotification(
              token = "push.key",
              details = "roomId must not be null",
            )
        }
      }
    }
  })
