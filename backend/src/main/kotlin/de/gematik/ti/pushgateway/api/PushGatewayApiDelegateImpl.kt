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

package de.gematik.ti.pushgateway.api

import arrow.core.mapValuesNotNull
import arrow.core.raise.either
import de.gematik.ti.pushgateway.config.ProducerCondition
import de.gematik.ti.pushgateway.exception.InvalidEncryptedBatchRequestException
import de.gematik.ti.pushgateway.exception.InvalidPlainBatchRequestException
import de.gematik.ti.pushgateway.extension.log
import de.gematik.ti.pushgateway.extension.pushClientIdentifier
import de.gematik.ti.pushgateway.extension.pushClientInfo
import de.gematik.ti.pushgateway.extension.pushProvider
import de.gematik.ti.pushgateway.failure.PushkeyHasBeenRejectedPreviously
import de.gematik.ti.pushgateway.jms.ApnsJmsConsumer.Companion.QUEUE_APNS_ENCRYPTED
import de.gematik.ti.pushgateway.jms.ApnsJmsConsumer.Companion.QUEUE_APNS_PLAIN
import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_ENCRYPTED
import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_PLAIN
import de.gematik.ti.pushgateway.jms.model.EncryptedNotificationMessage
import de.gematik.ti.pushgateway.jms.model.PlainNotificationMessage
import de.gematik.ti.pushgateway.model.EncryptedBatchNotificationResponseBodyCreator
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.*
import de.gematik.ti.pushgateway.openapi.server.PushGatewayApiDelegate
import de.gematik.ti.pushgateway.service.NotificationPayloadService
import de.gematik.ti.pushgateway.service.RejectedPushKeyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.springframework.context.annotation.Conditional
import org.springframework.http.ResponseEntity
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component

/**
 * Implements the REST interface (by delegation) and so supports the fulfilment of A_27164
 *
 * @see <a
 *   href="https://gemspec.gematik.de/docs/gemF/gemF_PushNotification/gemF_PushNotification_V1.1.0/#A_27164">gemF_PushNotification_V1.1.0/A_27164</a>
 */
@Component
@Conditional(ProducerCondition::class)
class PushGatewayApiDelegateImpl(
  private val logger: Logger,
  private val notificationPayloadService: NotificationPayloadService,
  private val coroutineScope: CoroutineScope,
  private val jmsTemplate: JmsTemplate,
  private val rejectedPushKeyService: RejectedPushKeyService,
) : PushGatewayApiDelegate {

  override fun pushV1NotifyPlain(
    plainNotificationRequest: PlainNotificationRequest
  ): ResponseEntity<PushV1NotifyPlain200Response> {
    val context =
      notificationPayloadService.deliverableNotification(
        notification = plainNotificationRequest.notification
      )
    val (rejectedPreviously, otherFailures) =
      context.first.partition { it is PushkeyHasBeenRejectedPreviously }

    otherFailures.toSet().forEach { logger.warn(it.message) }

    val responseBody =
      PushV1NotifyPlain200Response(
        rejected =
          rejectedPreviously
            .map { it as PushkeyHasBeenRejectedPreviously }
            .map { it.pushClientInfo.pushKey }
      )

    coroutineScope.launch {
      context.second?.let { forwardPlainContent(it) }
      removeRejectedPushKeys(rejectedPushKeys = responseBody.rejected.toSet())
    }

    return ResponseEntity.ok(responseBody)
  }

  override fun pushV1NotifyBatchEncrypted(
    encryptedBatchNotificationRequest: EncryptedBatchNotificationRequest
  ): ResponseEntity<BatchNotificationResponse> {
    val multipleOccurrences =
      encryptedBatchNotificationRequest.notifications
        .map { it.id }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .toList()

    if (multipleOccurrences.isNotEmpty()) {
      throw InvalidEncryptedBatchRequestException(
        error = PushV1NotifyBatchEncrypted400Response.Error.DUPLICATE_NOTIFICATION_IDS_IN_BATCH,
        details = PushV1NotifyBatchEncrypted400ResponseDetails(duplicateIds = multipleOccurrences),
      )
    }

    val context = notificationPayloadService.validateForDelivery(encryptedBatchNotificationRequest)
    val validated = context.filterValues { it.isRight() }.mapValuesNotNull { it.value.getOrNull() }
    val failures = context.filterValues { it.isLeft() }.mapValuesNotNull { it.value.leftOrNull() }

    val responseBody =
      EncryptedBatchNotificationResponseBodyCreator.from(validated = validated, failures = failures)

    coroutineScope.launch {
      validated
        .map { (_, notification) ->
          either {
              val pushClientInfo = notification.device.pushClientInfo.bind()
              EncryptedNotificationMessage(
                notification = notification,
                pushClientInfo = pushClientInfo,
                queue =
                  when (pushClientInfo.pushProvider) {
                    PushProvider.ANDROID -> QUEUE_FIREBASE_ENCRYPTED
                    PushProvider.IOS -> QUEUE_APNS_ENCRYPTED
                  },
              )
            }
            .onRight { message -> jmsTemplate.convertAndSend(message.queue, message) }
            .also { logger.log(it) }
        }
        .also {
          removeRejectedPushKeys(
            rejectedPushKeys = responseBody.results.mapNotNull { it.rejected }.flatten().toSet()
          )
        }
    }

    return ResponseEntity.ok(responseBody)
  }

  override fun pushV1NotifyBatchPlain(
    batchNotificationRequest: BatchNotificationRequest
  ): ResponseEntity<BatchNotificationResponse> {
    val multipleOccurrences =
      batchNotificationRequest.notifications
        .map { it.id }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .toList()

    if (multipleOccurrences.isNotEmpty()) {
      throw InvalidPlainBatchRequestException(
        error = PushV1NotifyBatchPlain400Response.Error.DUPLICATE_NOTIFICATION_IDS_IN_BATCH,
        details = PushV1NotifyBatchPlain400ResponseDetails(duplicateIds = multipleOccurrences),
      )
    }

    val allContexts = notificationPayloadService.validateForDelivery(batchNotificationRequest)
    val results =
      allContexts.map { (id, context) ->
        val (rejectedPreviously, otherFailures) =
          context.first.partition { it is PushkeyHasBeenRejectedPreviously }

        context.second?.let { coroutineScope.launch { forwardPlainContent(it) } }

        val original = batchNotificationRequest.notifications.first { it.id == id }.notification
        when (context.second) {
          null ->
            BatchNotificationResponseResultsInner(
              id = id,
              status = BatchNotificationResponseResultsInner.Status.FAILED,
              rejected =
                rejectedPreviously
                  .map { it as PushkeyHasBeenRejectedPreviously }
                  .map { it.pushClientInfo.pushKey },
              error = otherFailures.joinToString("\n") { it.message },
            )

          original ->
            BatchNotificationResponseResultsInner(
              id = id,
              status = BatchNotificationResponseResultsInner.Status.SUCCESS,
              rejected =
                rejectedPreviously
                  .map { it as PushkeyHasBeenRejectedPreviously }
                  .map { it.pushClientInfo.pushKey },
              error = null,
            )

          else ->
            BatchNotificationResponseResultsInner(
              id = id,
              status = BatchNotificationResponseResultsInner.Status.PARTIAL,
              rejected =
                rejectedPreviously
                  .map { it as PushkeyHasBeenRejectedPreviously }
                  .map { it.pushClientInfo.pushKey },
              error = otherFailures.joinToString("\n") { it.message },
            )
        }
      }

    val responseBody =
      BatchNotificationResponse(
        results = results,
        summary =
          BatchNotificationResponseSummary(
            total = results.size,
            successful =
              results.count { it.status == BatchNotificationResponseResultsInner.Status.SUCCESS },
            failed =
              results.count { it.status == BatchNotificationResponseResultsInner.Status.FAILED },
            partial =
              results.count { it.status == BatchNotificationResponseResultsInner.Status.PARTIAL },
          ),
      )

    coroutineScope.launch {
      removeRejectedPushKeys(
        rejectedPushKeys = results.mapNotNull { it.rejected }.flatten().toSet()
      )
    }

    return ResponseEntity.ok(responseBody)
  }

  private fun forwardPlainContent(notification: PlainNotification) {
    notification.devices.forEach { device ->
      either {
          val pushClientIdentifier = device.pushClientIdentifier.bind()
          val pushProvider = device.pushProvider.bind()
          val queue =
            when (pushProvider) {
              PushProvider.ANDROID -> QUEUE_FIREBASE_PLAIN
              PushProvider.IOS -> QUEUE_APNS_PLAIN
            }

          PlainNotificationMessage(
            notification = notification,
            pushClientInfo =
              PushClientInfo(
                appId = device.appId,
                pushKey = device.pushkey,
                pushClientIdentifier = pushClientIdentifier,
                pushProvider = pushProvider,
              ),
            queue = queue,
          )
        }
        .onRight { message -> jmsTemplate.convertAndSend(message.queue, message) }
        .also { logger.log(it) }
    }
  }

  private fun removeRejectedPushKeys(rejectedPushKeys: Set<String>) {
    rejectedPushKeyService.removeAllRejectedPushKeys(rejectedPushKeys).also { logger.log(it) }
  }
}
