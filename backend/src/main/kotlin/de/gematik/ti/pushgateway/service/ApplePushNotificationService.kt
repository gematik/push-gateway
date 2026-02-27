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
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.eatthepath.pushy.apns.ApnsClient
import com.eatthepath.pushy.apns.PushNotificationResponse
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.toApnsNotification
import de.gematik.ti.pushgateway.failure.APNSFailure
import de.gematik.ti.pushgateway.failure.ApnsNotificationRejected
import de.gematik.ti.pushgateway.failure.CouldNotFindApnsClient
import de.gematik.ti.pushgateway.failure.CouldNotSendApnsNotification
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(ConsumerCondition::class)
class ApplePushNotificationService(
  private val logger: Logger,
  private val pushClientProvider: PushClientProvider,
) {

  companion object {
    private const val TIMEOUT = 10_000L // ms
  }

  @WithSpan("apns_send_plain_notification")
  suspend fun sendNotification(
    pushClientInfo: PushClientInfo,
    notification: PlainNotification,
  ): Either<APNSFailure, Unit> = either {
    val span =
      Span.current().apply {
        setAttribute("pgw.apns.appId", pushClientInfo.appId)
        if (logger.isDebugEnabled) {
          setAttribute("pgw.apns.pushKey", pushClientInfo.pushKey)
        }
      }

    val apnsClient = findApnsClient(pushClientInfo).bind()

    val pushNotification =
      notification
        .toApnsNotification(pushClientInfo = pushClientInfo)
        .onLeft {
          span.addEvent(
            "apns.notification.not.created",
            Attributes.builder().put("fcm.details", it.message).build(),
          )
        }
        .onRight { span.addEvent("apns.notification.created") }
        .bind()

    val response =
      getApnsResponse(
          pushClientInfo = pushClientInfo,
          pushNotification = pushNotification,
          apnsClient = apnsClient,
        )
        .bind()

    validateApnsResponse(
        pushClientInfo = pushClientInfo,
        pushNotification = pushNotification,
        response = response,
      )
      .onRight { span.addEvent("apns.notification.sent.success") }
      .bind()
  }

  @WithSpan("apns_send_encrypted_notification")
  suspend fun sendNotification(
    pushClientInfo: PushClientInfo,
    notification: EncryptedNotification,
  ): Either<APNSFailure, Unit> = either {
    val span =
      Span.current().apply {
        setAttribute("pgw.apns.appId", pushClientInfo.appId)
        if (logger.isDebugEnabled) {
          setAttribute("pgw.apns.pushKey", pushClientInfo.pushKey)
        }
      }

    val apnsClient = findApnsClient(pushClientInfo = pushClientInfo).bind()

    val pushNotification =
      notification
        .toApnsNotification(pushClientInfo = pushClientInfo)
        .onLeft {
          span.addEvent(
            "apns.notification.not.created",
            Attributes.builder().put("details", it.message).build(),
          )
        }
        .onRight { span.addEvent("apns.notification.created") }
        .bind()

    getApnsResponse(
        pushClientInfo = pushClientInfo,
        pushNotification = pushNotification,
        apnsClient = apnsClient,
      )
      .flatMap { response ->
        validateApnsResponse(
          pushClientInfo = pushClientInfo,
          pushNotification = pushNotification,
          response = response,
        )
      }
      .onRight { span.addEvent("apns.notification.sent.success") }
      .bind()
  }

  private fun findApnsClient(
    pushClientInfo: PushClientInfo
  ): Either<CouldNotFindApnsClient, ApnsClient> =
    with(Span.current()) {
      pushClientProvider
        .apnsClientFor(bundleId = pushClientInfo.pushClientIdentifier)
        .onLeft {
          addEvent(
            "apns.client.notFound",
            Attributes.builder().put("bundleId", pushClientInfo.pushClientIdentifier.value).build(),
          )
        }
        .onRight {
          addEvent(
            "apns.client.found",
            Attributes.builder().put("bundleId", pushClientInfo.pushClientIdentifier.value).build(),
          )
        }
    }

  private suspend fun getApnsResponse(
    pushClientInfo: PushClientInfo,
    pushNotification: SimpleApnsPushNotification,
    apnsClient: ApnsClient,
  ): Either<CouldNotSendApnsNotification, PushNotificationResponse<SimpleApnsPushNotification>> =
    either {
      val span = Span.current()

      Either.catch {
          logger.debug(
            "[APNS] Trying to send plain push notification with pushKey={}",
            pushClientInfo.pushKey,
          )
          withTimeout(TIMEOUT) {
            apnsClient
              .sendNotification(pushNotification)
              .also { span.addEvent("apns.notification.sent") }
              .await()
          }
        }
        .mapLeft {
          span.addEvent(
            "apns.notification.sent.failure",
            Attributes.builder().put("details", it.message).build(),
          )
          CouldNotSendApnsNotification(
            pushKey = pushClientInfo.pushKey,
            notification = pushNotification,
            exception = it,
          )
        }
        .onRight { span.addEvent("apns.notification.sent.delivered") }
        .bind()
    }

  private fun validateApnsResponse(
    pushClientInfo: PushClientInfo,
    pushNotification: SimpleApnsPushNotification,
    response: PushNotificationResponse<SimpleApnsPushNotification>,
  ): Either<ApnsNotificationRejected, Unit> = either {
    val span = Span.current()

    ensure(response.isAccepted) {
      val reason = response.rejectionReason.orElse("not specified")
      span.addEvent(
        "apns.notification.rejected",
        Attributes.builder()
          .put("reason", reason)
          .also {
            if (logger.isDebugEnabled) {
              it.put("pushKey", pushClientInfo.pushKey)
            }
          }
          .build(),
      )
      ApnsNotificationRejected(
        pushKey = pushClientInfo.pushKey,
        notification = pushNotification,
        rejectionReason = reason,
      )
    }
  }
}
