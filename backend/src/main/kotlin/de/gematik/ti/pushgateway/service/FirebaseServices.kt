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

import arrow.core.Either
import arrow.core.raise.either
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.toFirebaseMessage
import de.gematik.ti.pushgateway.failure.CouldNotSendFirebaseMessage
import de.gematik.ti.pushgateway.failure.FirebaseFailure
import de.gematik.ti.pushgateway.failure.FirebaseServiceFailure
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import io.micrometer.core.annotation.Counted
import io.micrometer.core.annotation.Timed
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@JvmInline value class FirebaseMessageId(val value: String)

/**
 * Implements A_27165-01 - Push Gateway - Push Notification senden – Aufruf Push Provider for
 * Firebase
 *
 * @see <a
 *   href="https://gemspec.gematik.de/docs/gemF/gemF_PushNotification/gemF_PushNotification_V1.1.0/#A_27165-01">gemF_PushNotification_V1.1.0/#A_27165-01</a>
 */
@Service
@Conditional(ConsumerCondition::class)
class FirebaseService(
  private val logger: Logger,
  private val pushClientProvider: PushClientProvider,
  private val sendService: InstrumentedFirebaseSendService,
) {

  @WithSpan("fcm_send_plain_notification")
  fun sendNotification(
    pushClientInfo: PushClientInfo,
    notification: PlainNotification,
  ): Either<FirebaseFailure, FirebaseMessageId> = either {
    val span =
      Span.current().apply {
        setAttribute("pgw.fcm.appId", pushClientInfo.appId)
        if (logger.isDebugEnabled) {
          setAttribute("pgw.fcm.pushKey", pushClientInfo.pushKey)
        }
      }

    val fmc = findFirebaseClient(pushClientIdentifier = pushClientInfo.pushClientIdentifier).bind()

    val message =
      notification
        .toFirebaseMessage(pushKey = pushClientInfo.pushKey)
        .onLeft {
          span.addEvent(
            "fcm.notification.not.created",
            Attributes.builder().put("details", it.message).build(),
          )
        }
        .onRight { span.addEvent("fcm.notification.created") }
        .bind()

    sendService.sendMessage(fmc = fmc, message = message, pushKey = pushClientInfo.pushKey).bind()
  }

  @WithSpan("fcm_send_encrypted_notification")
  fun sendNotification(
    pushClientInfo: PushClientInfo,
    notification: EncryptedNotification,
  ): Either<FirebaseFailure, FirebaseMessageId> = either {
    val span =
      Span.current().apply {
        setAttribute("pgw.fcm.appId", pushClientInfo.appId)
        if (logger.isDebugEnabled) {
          setAttribute("pgw.fcm.pushKey", pushClientInfo.pushKey)
        }
      }

    val fmc =
      pushClientProvider.firebaseClientFor(projectId = pushClientInfo.pushClientIdentifier).bind()

    val message =
      notification
        .toFirebaseMessage()
        .onLeft {
          // this should actually never happen because the type system ensures non-null values
          // where needed
          span.addEvent(
            "fcm.notification.not.created",
            Attributes.builder().put("details", it.message).build(),
          )
        }
        .onRight { span.addEvent("fcm.notification.created") }
        .bind()

    sendService.sendMessage(fmc = fmc, message = message, pushKey = pushClientInfo.pushKey).bind()
  }

  private fun findFirebaseClient(pushClientIdentifier: PushClientIdentifier) =
    with(Span.current()) {
      pushClientProvider
        .firebaseClientFor(projectId = pushClientIdentifier)
        .onLeft {
          addEvent(
            "fcm.client.notFound",
            Attributes.builder().put("projectId", pushClientIdentifier.value).build(),
          )
        }
        .onRight {
          addEvent(
            "fcm.client.found",
            Attributes.builder().put("projectId", pushClientIdentifier.value).build(),
          )
        }
    }
}

@Service
@Conditional(ConsumerCondition::class)
class InstrumentedFirebaseSendService {

  @Timed(
    value = "fcm_notifications_sent_timer_seconds",
    description = "Firebase notifications sent timer seconds",
    histogram = true,
  )
  @Counted(value = "fcm_notifications")
  fun sendMessage(
    fmc: FirebaseMessaging,
    message: Message,
    pushKey: String,
  ): Either<FirebaseFailure, FirebaseMessageId> =
    with(Span.current()) {
      Either.catch { fmc.send(message).also { addEvent("fcm.notification.sent") } }
        .onRight {
          addEvent(
            "fcm.notification.sent.success",
            Attributes.builder().put("firebaseMessageId", it).build(),
          )
        }
        .onLeft {
          addEvent(
            "fcm.notification.sent.failure",
            Attributes.builder().put("details", it.message).build(),
          )
        }
        .map { FirebaseMessageId(value = it) }
        .mapLeft { failure ->
          if (failure is FirebaseMessagingException) {
            FirebaseServiceFailure(token = pushKey, errorCode = failure.messagingErrorCode)
          } else {
            CouldNotSendFirebaseMessage(token = pushKey, exception = failure)
          }
        }
    }
}
