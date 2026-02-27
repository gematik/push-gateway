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

import de.gematik.ti.pushgateway.component.CoroutineListenerScope
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.log
import de.gematik.ti.pushgateway.failure.APNSFailure
import de.gematik.ti.pushgateway.failure.ApnsNotificationRejected
import de.gematik.ti.pushgateway.jms.model.EncryptedNotificationMessage
import de.gematik.ti.pushgateway.jms.model.PlainNotificationMessage
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyEntity
import de.gematik.ti.pushgateway.service.ApplePushNotificationService
import de.gematik.ti.pushgateway.service.RejectedPushKeyService
import jakarta.jms.Message
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.event.Level
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Conditional
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

/**
 * Implements A_27165-01 - Push Gateway - Push Notification senden – Aufruf Push Provider for APNS
 *
 * @see <a
 *   href="https://gemspec.gematik.de/docs/gemF/gemF_PushNotification/gemF_PushNotification_V1.1.0/#A_27165-01">gemF_PushNotification_V1.1.0/#A_27165-01</a>
 */
@ConditionalOnProperty(name = ["gatling.enabled"], havingValue = "false", matchIfMissing = true)
@Conditional(ConsumerCondition::class)
@Component
class ApnsJmsConsumer(
  private val logger: Logger,
  private val applePushNotificationService: ApplePushNotificationService,
  private val rejectedPushKeyService: RejectedPushKeyService,
  private val coroutineScope: CoroutineListenerScope,
) : JmsConsumer {

  companion object {
    const val QUEUE_APNS_PLAIN = "pending.notifications.apns.plain"
    const val QUEUE_APNS_ENCRYPTED = "pending.notifications.apns.encrypted"
  }

  @JmsListener(destination = QUEUE_APNS_PLAIN)
  override fun processPlainNotification(payload: PlainNotificationMessage, message: Message) {
    coroutineScope.launch {
      logger.debug("[APNS] Received plain notification")
      applePushNotificationService
        .sendNotification(
          pushClientInfo = payload.pushClientInfo,
          notification = payload.notification,
        )
        .also {
          logger.log(it) {
            Pair(
              "[APNS] Accepted push notification for ${payload.pushClientInfo.appId}",
              Level.INFO,
            )
          }
        }
        .onRight { message.acknowledge() }
        .onLeft {
          handleFailure(message = message, pushClientInfo = payload.pushClientInfo, failure = it)
        }
    }
  }

  @JmsListener(destination = QUEUE_APNS_ENCRYPTED)
  override fun processEncryptedNotification(
    payload: EncryptedNotificationMessage,
    message: Message,
  ) {
    coroutineScope.launch {
      logger.debug("[APNS] Received encrypted notification")
      applePushNotificationService
        .sendNotification(
          pushClientInfo = payload.pushClientInfo,
          notification = payload.notification,
        )
        .also {
          logger.log(it) {
            Pair(
              "[APNS] Accepted push notification for ${payload.pushClientInfo.appId}",
              Level.INFO,
            )
          }
        }
        .onRight { message.acknowledge() }
        .onLeft {
          handleFailure(message = message, pushClientInfo = payload.pushClientInfo, failure = it)
        }
    }
  }

  private fun handleFailure(
    message: Message,
    pushClientInfo: PushClientInfo,
    failure: APNSFailure,
  ) {
    when (failure) {
      is ApnsNotificationRejected -> {
        rejectedPushKeyService.addRejectedPushKey(
          rejectedPushKeyEntity =
            RejectedPushKeyEntity(
              pushKey = failure.pushKey,
              appId = pushClientInfo.appId,
              reason = failure.rejectionReason,
            )
        )
      }
      else -> Unit
    }

    if (!failure.isRetrySensible) {
      message.acknowledge().also {
        if (logger.isDebugEnabled) {
          logger.debug(
            "[APNS] Delivery to push provider will not be retried, appId={}, pushKey={}",
            pushClientInfo.appId,
            pushClientInfo.pushKey,
          )
        } else {
          logger.info(
            "[APNS] Delivery to push provider will not be retried, appId={}",
            pushClientInfo.appId,
          )
        }
      }
    }
  }
}
