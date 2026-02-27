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

import com.google.firebase.messaging.MessagingErrorCode
import de.gematik.ti.pushgateway.model.PushClientIdentifier

sealed interface FirebaseFailure : GeneralFailure {
  val isRetrySensible: Boolean
}

sealed interface FirebaseClientFailure : FirebaseFailure

sealed interface FirebaseNotificationFailure : FirebaseFailure {
  val token: String
}

sealed interface FirebaseConfigFailure : FirebaseFailure {
  override val isRetrySensible: Boolean
    get() = false
}

data class CouldNotReadFirebaseCredentials(val credentialsPath: String) : FirebaseConfigFailure {
  override val message: String
    get() =
      "[FIREBASE] Could not read credentials from path: $credentialsPath, no client will be created"
}

data class CouldNotFindFirebaseClient(val projectId: PushClientIdentifier) : FirebaseClientFailure {
  override val message: String
    get() = "[FIREBASE] Could not find push client for projectId=${projectId.value}"

  // in case config can be corrected within 24 h
  override val isRetrySensible: Boolean = true
}

data class CouldNotBuildFirebasePushNotification(override val token: String, val details: String) :
  FirebaseNotificationFailure {
  override val message: String
    get() = "[FIREBASE] Could not build push notification: $details"

  // invalid payload can never turn into valid firebase message, so retrying makes no sense
  override val isRetrySensible: Boolean = false
}

data class CouldNotBuildFirebaseMessage(
  override val token: String,
  override val exception: Throwable,
) : FirebaseNotificationFailure {
  override val message: String
    get() = "[FIREBASE] Could not build push notification: {}"

  // invalid payload can never turn into valid firebase message, so retrying makes no sense
  override val isRetrySensible: Boolean = false
}

data class CouldNotSendFirebaseMessage(
  override val token: String,
  override val exception: Throwable,
) : FirebaseNotificationFailure {
  override val message: String
    get() = "[FIREBASE] Could not send push notification: {}"

  override val isRetrySensible: Boolean = true
}

data class FirebaseServiceFailure(override val token: String, val errorCode: MessagingErrorCode) :
  FirebaseNotificationFailure {
  override val message: String
    get() = "[FIREBASE] Denied push notification for pushKey=$token: ${errorCode.name}"

  override val isRetrySensible: Boolean =
    when (errorCode) {
      MessagingErrorCode.INVALID_ARGUMENT,
      MessagingErrorCode.SENDER_ID_MISMATCH,
      MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
      MessagingErrorCode.UNREGISTERED -> false
      MessagingErrorCode.INTERNAL,
      MessagingErrorCode.QUOTA_EXCEEDED,
      MessagingErrorCode.UNAVAILABLE -> true
    }
}
