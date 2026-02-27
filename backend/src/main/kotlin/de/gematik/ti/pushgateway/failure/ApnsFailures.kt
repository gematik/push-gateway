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

package de.gematik.ti.pushgateway.failure

import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import de.gematik.ti.pushgateway.model.PushClientIdentifier

sealed interface APNSFailure : GeneralFailure {
  val isRetrySensible: Boolean
}

sealed interface APNSClientFailure : APNSFailure

sealed interface APNSNotificationFailure : APNSFailure {
  val pushKey: String
}

sealed interface APNSConfigFailure : APNSFailure {
  override val isRetrySensible: Boolean
    get() = false
}

data class ApnsTokenConfigNotProvided(val bundleId: String) : APNSConfigFailure {
  override val isRetrySensible: Boolean = false
  override val message: String
    get() = "[APNS] Token config not provided, for $bundleId no client will be created"
}

data class ApnsCertificateConfigNotProvided(val bundleId: String) : APNSConfigFailure {
  override val isRetrySensible: Boolean = false
  override val message: String
    get() = "[APNS] Certificate config not provided, for $bundleId no client will be created"
}

data class CouldNotReadApnsCertificate(val certificatePath: String) : APNSConfigFailure {
  override val message: String
    get() =
      "[APNS] Could not read certificate from path: $certificatePath, no client will be created"
}

data class CouldNotReadApnsToken(val tokenPath: String) : APNSConfigFailure {
  override val message: String
    get() = "[APNS] Could not read token from path: $tokenPath, no client will be created"
}

data class CouldNotFindApnsClient(val bundleId: PushClientIdentifier) : APNSClientFailure {
  override val message: String
    get() = "[APNS] Could not find push client for bundleId=${bundleId.value}"

  // in case config can be fixed within 24 h
  override val isRetrySensible: Boolean = true
}

data class CouldNotBuildApnsPushNotificationPayload(
  override val pushKey: String,
  val details: String,
) : APNSNotificationFailure {
  override val message: String
    get() = "[APNS] Could not build push notification payload: $details"

  // invalid payload can never turn into a valid APNs message, so retrying is senseless
  override val isRetrySensible: Boolean = false
}

data class CouldNotSendApnsNotification(
  override val pushKey: String,
  val notification: SimpleApnsPushNotification,
  override val exception: Throwable,
) : APNSNotificationFailure {
  override val message: String
    get() = "[APNS] Could not send push notification: {}"

  // this should only happen when APNs is temporarily not available, so retrying makes sense
  override val isRetrySensible: Boolean = true
}

data class ApnsNotificationRejected(
  override val pushKey: String,
  val notification: SimpleApnsPushNotification,
  val rejectionReason: String,
) : APNSNotificationFailure {
  override val message: String
    get() = "[APNS] Denied push notification for pushKey=$pushKey: $rejectionReason"

  // once rejected, the pushkey will always be rejected again, so retrying makes no sense
  override val isRetrySensible: Boolean = false
}
