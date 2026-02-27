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

package com.google.firebase.messaging

import com.google.firebase.ErrorCode
import com.google.firebase.FirebaseException
import de.gematik.ti.pushgateway.failure.CouldNotSendFirebaseMessage
import de.gematik.ti.pushgateway.failure.FirebaseServiceFailure
import de.gematik.ti.pushgateway.service.FirebaseMessageId
import de.gematik.ti.pushgateway.service.InstrumentedFirebaseSendService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

class InstrumentedFirebaseSendServiceTest :
  ShouldSpec({
    val fmc = mockk<FirebaseMessaging>(relaxed = true)
    val sut = InstrumentedFirebaseSendService()

    beforeTest { clearAllMocks() }

    context("right") {
      should("send message to firebase") {
        every { fmc.send(any()) } returns "some-message-id"
        sut.sendMessage(
          fmc = fmc,
          message = mockk(relaxed = true),
          pushKey = "valid.pushKey",
        ) shouldBeRight FirebaseMessageId(value = "some-message-id")
      }
    }

    context("left") {
      should("handle firebase message error") {
        val base = FirebaseException(ErrorCode.INTERNAL, "some error", null)
        val error =
          FirebaseMessagingException.withMessagingErrorCode(base, MessagingErrorCode.INTERNAL)
        every { fmc.send(any()) } throws error
        sut.sendMessage(fmc, mockk(), "valid.pushKey") shouldBeLeft
          FirebaseServiceFailure(token = "valid.pushKey", errorCode = error.messagingErrorCode)
      }
      should("handle firebase error") {
        val error = FirebaseException(ErrorCode.INTERNAL, "some error", null)
        every { fmc.send(any()) } throws error
        sut.sendMessage(fmc, mockk(), "valid.pushKey") shouldBeLeft
          CouldNotSendFirebaseMessage(token = "valid.pushKey", exception = error)
      }
    }
  })
