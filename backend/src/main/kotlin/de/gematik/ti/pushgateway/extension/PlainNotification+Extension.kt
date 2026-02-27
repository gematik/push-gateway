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
import arrow.core.raise.ensureNotNull
import com.eatthepath.pushy.apns.DeliveryPriority
import com.eatthepath.pushy.apns.PushType
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.eatthepath.pushy.apns.util.TokenUtil
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import de.gematik.ti.pushgateway.failure.CouldNotBuildApnsPushNotificationPayload
import de.gematik.ti.pushgateway.failure.CouldNotBuildFirebaseMessage
import de.gematik.ti.pushgateway.failure.CouldNotBuildFirebasePushNotification
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import de.gematik.ti.pushgateway.openapi.model.Priority

/**
 * Implements A_27167 - Push Gateway - Prio Feld mappen
 *
 * @see <a
 *   href="https://gemspec.gematik.de/docs/gemF/gemF_PushNotification/gemF_PushNotification_V1.1.0/#A_27167">gemF_PushNotification_V1.1.0/#A_27167</a>
 */
val PlainNotification.apnsPriority
  get() =
    when (prio) {
      Priority.LOW -> DeliveryPriority.CONSERVE_POWER
      Priority.HIGH -> DeliveryPriority.IMMEDIATE
    }

fun PlainNotification.toApnsNotification(pushClientInfo: PushClientInfo) = either {
  SimpleApnsPushNotification(
    TokenUtil.sanitizeTokenString(pushClientInfo.pushKey),
    pushClientInfo.pushClientIdentifier.value,
    toApnsPayload(pushKey = pushClientInfo.pushKey).bind(),
    null,
    apnsPriority,
    PushType.ALERT,
  )
}

fun PlainNotification.toApnsPayload(
  pushKey: String,
  builder: SimpleApnsPayloadBuilder = SimpleApnsPayloadBuilder(),
) = either {
  ensureNotNull(eventId) {
    CouldNotBuildApnsPushNotificationPayload(
      pushKey = pushKey,
      details = "eventId must not be null",
    )
  }

  ensureNotNull(roomId) {
    CouldNotBuildApnsPushNotificationPayload(pushKey = pushKey, details = "roomId must not be null")
  }

  Either.catch {
      builder
        .setMutableContent(true)
        .setAlertTitle("title")
        .setAlertSubtitle("subtitle")
        .setAlertBody("body")
        .addCustomProperty("event_id", eventId)
        .addCustomProperty("room_id", roomId)
        .apply {
          content?.let { addCustomProperty("content", it) }
          roomAlias?.let { addCustomProperty("room_alias", it) }
          roomName?.let { addCustomProperty("room_name", it) }
          sender?.let { addCustomProperty("sender", it) }
          senderDisplayName?.let { addCustomProperty("sender_display_name", it) }
          type?.let { addCustomProperty("type", it) }
        }
        .build()
    }
    .mapLeft {
      CouldNotBuildApnsPushNotificationPayload(
        pushKey = pushKey,
        details = "Custom property key must not be aps",
      )
    }
    .bind()
}

fun PlainNotification.toFirebaseMessage(
  pushKey: String,
  builder: Message.Builder = Message.builder(),
) = either {
  ensureNotNull(eventId) {
    CouldNotBuildFirebasePushNotification(token = pushKey, details = "eventId must not be null")
  }

  ensureNotNull(roomId) {
    CouldNotBuildFirebasePushNotification(token = pushKey, details = "roomId must not be null")
  }

  Either.catch {
      builder
        .putAllData(
          mapOf(
              "content" to content?.toString(),
              "event_id" to eventId,
              "prio" to prio.value,
              "room_alias" to roomAlias,
              "room_id" to roomId,
              "room_name" to roomName,
              "sender" to sender,
              "sender_display_name" to senderDisplayName,
              "type" to type,
            )
            .filterValues { it != null }
        )
        .setNotification(
          Notification.builder().setTitle(roomName ?: "").setBody(roomAlias ?: "").build()
        )
        .setToken(pushKey)
        .build()
    }
    .mapLeft { CouldNotBuildFirebaseMessage(token = pushKey, exception = it) }
    .bind()
}
