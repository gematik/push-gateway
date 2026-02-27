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

package de.gematik.ti.pushgateway.jms

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.log
import de.gematik.ti.pushgateway.failure.FirebaseFailure
import de.gematik.ti.pushgateway.jms.model.EncryptedNotificationMessage
import de.gematik.ti.pushgateway.jms.model.PlainNotificationMessage
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyEntity
import de.gematik.ti.pushgateway.service.FirebaseService
import de.gematik.ti.pushgateway.service.RejectedPushKeyService
import jakarta.jms.Message
import org.slf4j.Logger
import org.slf4j.event.Level
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Conditional
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

@ConditionalOnProperty(name = ["gatling.enabled"], havingValue = "false", matchIfMissing = true)
@Conditional(ConsumerCondition::class)
@Component
class FirebaseJmsConsumer(
  private val logger: Logger,
  private val firebaseService: FirebaseService,
  private val rejectedPushKeyService: RejectedPushKeyService,
) : JmsConsumer {

  companion object {
    const val QUEUE_FIREBASE_PLAIN = "pending.notifications.firebase.plain"
    const val QUEUE_FIREBASE_ENCRYPTED = "pending.notifications.firebase.encrypted"
  }

  @JmsListener(destination = QUEUE_FIREBASE_PLAIN)
  override fun processPlainNotification(payload: PlainNotificationMessage, message: Message) {
    logger.debug("[FIREBASE] Received plain notification")
    firebaseService
      .sendNotification(
        pushClientInfo = payload.pushClientInfo,
        notification = payload.notification,
      )
      .also {
        logger.log(it) {
          Pair(
            "[FIREBASE] Accepted push notification for ${payload.pushClientInfo.appId}",
            Level.INFO,
          )
        }
      }
      .onRight { message.acknowledge() }
      .onLeft {
        handleLeftFirebase(message = message, pushClientInfo = payload.pushClientInfo, failure = it)
      }
  }

  @JmsListener(destination = QUEUE_FIREBASE_ENCRYPTED)
  override fun processEncryptedNotification(
    payload: EncryptedNotificationMessage,
    message: Message,
  ) {
    logger.debug("[FIREBASE] Received encrypted notification")
    firebaseService
      .sendNotification(
        pushClientInfo = payload.pushClientInfo,
        notification = payload.notification,
      )
      .also {
        logger.log(it) {
          Pair(
            "[FIREBASE] Accepted push notification for ${payload.pushClientInfo.appId}",
            Level.INFO,
          )
        }
      }
      .onRight { message.acknowledge() }
      .onLeft {
        handleLeftFirebase(message = message, pushClientInfo = payload.pushClientInfo, failure = it)
      }
  }

  private fun handleLeftFirebase(
    message: Message,
    pushClientInfo: PushClientInfo,
    failure: FirebaseFailure,
  ) {
    when (val exception = failure.exception) {
      is FirebaseMessagingException -> {
        when (exception.messagingErrorCode) {
          MessagingErrorCode.UNREGISTERED,
          MessagingErrorCode.SENDER_ID_MISMATCH -> {
            rejectedPushKeyService.addRejectedPushKey(
              rejectedPushKeyEntity =
                RejectedPushKeyEntity(
                  pushKey = pushClientInfo.pushKey,
                  appId = pushClientInfo.appId,
                  reason = exception.messagingErrorCode.name,
                )
            )
          }
          else -> Unit
        }
      }
      else -> Unit
    }

    if (!failure.isRetrySensible) {
      message.acknowledge().also {
        if (logger.isDebugEnabled) {
          logger.debug(
            "[FIREBASE] Delivery to push provider will not be retried, appId={}, pushKey={}",
            pushClientInfo.appId,
            pushClientInfo.pushKey,
          )
        } else {
          logger.info(
            "[FIREBASE] Delivery to push provider will not be retried, appId={}",
            pushClientInfo.appId,
          )
        }
      }
    }
  }
}
