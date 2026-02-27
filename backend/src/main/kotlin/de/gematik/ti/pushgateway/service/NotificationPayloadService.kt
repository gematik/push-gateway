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
import arrow.core.raise.ensure
import arrow.core.separateEither
import de.gematik.ti.pushgateway.config.ProducerCondition
import de.gematik.ti.pushgateway.extension.pushClientInfo
import de.gematik.ti.pushgateway.failure.CouldNotFindPushClient
import de.gematik.ti.pushgateway.failure.PushConversionFailure
import de.gematik.ti.pushgateway.failure.PushkeyHasBeenRejectedPreviously
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.*
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(ProducerCondition::class)
class NotificationPayloadService(
  private val rejectedPushKeyService: RejectedPushKeyService,
  private val pushClientProvider: PushClientProvider,
) {

  fun validateForDelivery(
    request: EncryptedBatchNotificationRequest
  ): Map<String, Either<PushConversionFailure, EncryptedNotification>> =
    request.notifications.associate { it.id to deliverableNotification(it.notification) }

  fun validateForDelivery(
    request: BatchNotificationRequest
  ): Map<String, Pair<List<PushConversionFailure>, PlainNotification?>> =
    request.notifications.associate { it.id to deliverableNotification(it.notification) }

  fun deliverableNotification(
    notification: EncryptedNotification
  ): Either<PushConversionFailure, EncryptedNotification> =
    validateDevice(notification.device).map { notification }

  fun deliverableNotification(
    notification: PlainNotification
  ): Pair<List<PushConversionFailure>, PlainNotification?> {
    val deviceValidationResults = notification.devices.map { validateDevice(it) }

    val (failures, validDevices) = deviceValidationResults.separateEither()

    return if (validDevices.isNotEmpty()) {
      failures to notification.copy(devices = validDevices.toSet())
    } else {
      failures to null
    }
  }

  private fun validateDevice(device: Device): Either<PushConversionFailure, Device> = either {
    // validate push client identifier and push provider
    val pushClientInfo = device.pushClientInfo.bind()

    // check if appropriate push client is configured
    when (pushClientInfo.pushProvider) {
      PushProvider.ANDROID ->
        ensure(
          condition =
            pushClientProvider.hasFirebaseClientFor(projectId = pushClientInfo.pushClientIdentifier)
        ) {
          CouldNotFindPushClient(device = device, pushClientInfo = pushClientInfo)
        }

      PushProvider.IOS ->
        ensure(
          condition =
            pushClientProvider.hasApnsClientFor(bundleId = pushClientInfo.pushClientIdentifier)
        ) {
          CouldNotFindPushClient(device = device, pushClientInfo = pushClientInfo)
        }
    }

    // validate that the device's pushkey has not been rejected previously
    val rejected =
      rejectedPushKeyService.findRejectedPushKeyEntities(listOf(device.pushkey)).map { it.pushKey }
    ensure(!rejected.contains(device.pushkey)) {
      PushkeyHasBeenRejectedPreviously(pushClientInfo = pushClientInfo)
    }

    device
  }
}
