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
import com.eatthepath.pushy.apns.ApnsClient
import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.failure.CouldNotFindApnsClient
import de.gematik.ti.pushgateway.failure.CouldNotFindFirebaseClient
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import io.mockk.mockkStatic
import org.slf4j.Logger

class PushClientProviderTest :
  ShouldSpec({
    mockkStatic(Logger::class)

    val logger = mockk<Logger>(relaxed = true)

    val pushClientProvider =
      PushClientProvider(
        logger = logger,
        apnsClients =
          mapOf(PushClientIdentifier(value = "bundle.id.apns") to mockk(relaxed = true)),
        firebaseClients =
          mapOf(PushClientIdentifier(value = "project.id.firebase") to mockk(relaxed = true)),
      )

    context("APNs") {
      should("find apns client by bundle.id.apns") {
        pushClientProvider.hasApnsClientFor(PushClientIdentifier("bundle.id.apns")) shouldBe true
        pushClientProvider
          .apnsClientFor(PushClientIdentifier("bundle.id.apns"))
          .shouldBeInstanceOf<Either.Right<ApnsClient>>()
      }

      should("not find apns client by unknown.bundle.id.apns") {
        pushClientProvider.hasApnsClientFor(PushClientIdentifier("unknown.bundle.id.apns")) shouldBe
          false
        pushClientProvider.apnsClientFor(
          PushClientIdentifier("unknown.bundle.id.apns")
        ) shouldBeLeft
          CouldNotFindApnsClient(bundleId = PushClientIdentifier(value = "unknown.bundle.id.apns"))
      }
    }

    context("Firebase") {
      should("find firebase client by project.id.firebase") {
        pushClientProvider.hasFirebaseClientFor(
          PushClientIdentifier("project.id.firebase")
        ) shouldBe true
        pushClientProvider
          .firebaseClientFor(PushClientIdentifier("project.id.firebase"))
          .shouldBeInstanceOf<Either.Right<FirebaseMessaging>>()
      }

      should("not find firebase client by unknown.project.id.firebase") {
        val projectId = "unknown.project.id.firebase"
        pushClientProvider.hasFirebaseClientFor(PushClientIdentifier(projectId)) shouldBe false
        pushClientProvider.firebaseClientFor(PushClientIdentifier(projectId)) shouldBeLeft
          CouldNotFindFirebaseClient(projectId = PushClientIdentifier(value = projectId))
      }
    }
  })
