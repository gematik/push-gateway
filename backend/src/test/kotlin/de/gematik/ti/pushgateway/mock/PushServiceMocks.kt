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

package de.gematik.ti.pushgateway.mock

import arrow.core.left
import arrow.core.right
import com.google.firebase.messaging.MessagingErrorCode
import de.gematik.ti.pushgateway.failure.ApnsNotificationRejected
import de.gematik.ti.pushgateway.failure.FirebaseServiceFailure
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import de.gematik.ti.pushgateway.service.ApplePushNotificationService
import de.gematik.ti.pushgateway.service.FirebaseMessageId
import de.gematik.ti.pushgateway.service.FirebaseService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class PushServiceMocks {

  @Bean
  @Primary
  fun firebaseServiceMock(): FirebaseService =
    mockk(relaxed = true) {
      every {
        sendNotification(
          pushClientInfo = any<PushClientInfo>(),
          notification = any<PlainNotification>(),
        )
      } answers
        {
          val pushClientInfo = firstArg<PushClientInfo>()
          if (pushClientInfo.pushKey.contains("unregistered")) {
            FirebaseServiceFailure(
                token = pushClientInfo.pushKey,
                errorCode = MessagingErrorCode.UNREGISTERED,
              )
              .left()
          } else if (pushClientInfo.pushKey.contains("quota")) {
            FirebaseServiceFailure(
                token = pushClientInfo.pushKey,
                errorCode = MessagingErrorCode.QUOTA_EXCEEDED,
              )
              .left()
          } else {
            FirebaseMessageId(value = "").right()
          }
        }

      every {
        sendNotification(
          pushClientInfo = any<PushClientInfo>(),
          notification = any<EncryptedNotification>(),
        )
      } answers
        {
          val pushClientInfo = firstArg<PushClientInfo>()
          if (pushClientInfo.pushKey.contains("unregistered")) {
            FirebaseServiceFailure(
                token = pushClientInfo.pushKey,
                errorCode = MessagingErrorCode.UNREGISTERED,
              )
              .left()
          } else if (pushClientInfo.pushKey.contains("quota")) {
            FirebaseServiceFailure(
                token = pushClientInfo.pushKey,
                errorCode = MessagingErrorCode.QUOTA_EXCEEDED,
              )
              .left()
          } else {
            FirebaseMessageId(value = "").right()
          }
        }
    }

  @Bean
  @Primary
  fun apnServiceMock(): ApplePushNotificationService =
    mockk(relaxed = true) {
      coEvery {
        sendNotification(
          pushClientInfo = any<PushClientInfo>(),
          notification = any<PlainNotification>(),
        )
      } answers
        {
          val pushClientInfo = firstArg<PushClientInfo>()
          if (pushClientInfo.pushKey.contains("rejected")) {
            ApnsNotificationRejected(
                pushKey = pushClientInfo.pushKey,
                notification = mockk(relaxed = true),
                rejectionReason = "rejected for some reason",
              )
              .left()
          } else {
            Unit.right()
          }
        }

      coEvery {
        sendNotification(
          pushClientInfo = any<PushClientInfo>(),
          notification = any<EncryptedNotification>(),
        )
      } answers
        {
          val pushClientInfo = firstArg<PushClientInfo>()
          if (pushClientInfo.pushKey.contains("rejected")) {
            ApnsNotificationRejected(
                pushKey = pushClientInfo.pushKey,
                notification = mockk(relaxed = true),
                rejectionReason = "rejected for some reason",
              )
              .left()
          } else {
            Unit.right()
          }
        }
    }
}
