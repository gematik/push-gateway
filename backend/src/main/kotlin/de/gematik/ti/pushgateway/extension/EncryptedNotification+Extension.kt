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

import arrow.core.Either
import arrow.core.raise.either
import com.eatthepath.pushy.apns.DeliveryPriority
import com.eatthepath.pushy.apns.PushType
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.eatthepath.pushy.apns.util.TokenUtil
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import de.gematik.ti.pushgateway.failure.CouldNotBuildApnsPushNotificationPayload
import de.gematik.ti.pushgateway.failure.CouldNotBuildFirebaseMessage
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.Priority

val EncryptedNotification.deliveryPriority
  get() =
    when (prio) {
      Priority.LOW -> DeliveryPriority.CONSERVE_POWER
      Priority.HIGH,
      null -> DeliveryPriority.IMMEDIATE
    }

fun EncryptedNotification.toApnsNotification(pushClientInfo: PushClientInfo) = either {
  SimpleApnsPushNotification(
    TokenUtil.sanitizeTokenString(this@toApnsNotification.device.pushkey),
    pushClientInfo.pushClientIdentifier.value,
    toApnsPayload().bind(),
    null,
    deliveryPriority,
    PushType.ALERT,
  )
}

fun EncryptedNotification.toApnsPayload(
  builder: SimpleApnsPayloadBuilder = SimpleApnsPayloadBuilder()
) =
  Either.catch {
      builder
        .setAlertTitle("title")
        .setAlertSubtitle("subtitle")
        .setAlertBody("body")
        .setMutableContent(true)
        .addCustomProperty("ciphertext", ciphertext)
        .addCustomProperty("time_message_encrypted", timeMessageEncrypted)
        .addCustomProperty("key_identifier", keyIdentifier)
        .apply { identifier?.let { addCustomProperty("identifier", it) } }
        .build()
    }
    .mapLeft {
      CouldNotBuildApnsPushNotificationPayload(
        pushKey = this.device.pushkey,
        details = "Custom property key must not be aps",
      )
    }

fun EncryptedNotification.toFirebaseMessage(builder: Message.Builder = Message.builder()) =
  Either.catch {
      builder
        .putAllData(
          mapOf(
              "ciphertext" to ciphertext,
              "time_message_encrypted" to timeMessageEncrypted,
              "key_identifier" to keyIdentifier,
              "identifier" to identifier,
            )
            .filterValues { it != null }
        )
        .setNotification(Notification.builder().setTitle("title").setBody("body").build())
        .setToken(device.pushkey)
        .build()
    }
    .mapLeft { CouldNotBuildFirebaseMessage(token = device.pushkey, exception = it) }
