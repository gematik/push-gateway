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

package de.gematik.ti.pushgateway.validation

import de.gematik.ti.pushgateway.config.AndroidPushConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class AndroidPushConfigValidatorTest :
  ShouldSpec({
    val sut = AndroidPushConfigValidator()

    context("pass validation") {
      should("pass validation with proper input") {
        val subject =
          AndroidPushConfig(
            enabled = true,
            projectId = "project-id",
            credentialsPath = "pathToServiceAccountJsonFile",
            receivingAppIds = setOf("project-id.appOne.firebase"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe true
      }

      should("pass validation when disabled") {
        val subject =
          AndroidPushConfig(
            enabled = false,
            projectId = "",
            credentialsPath = "",
            receivingAppIds = emptySet(),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe true
      }
    }

    context("fail validation") {
      should("fail validation without or blank projectId") {
        val subject =
          AndroidPushConfig(
            enabled = true,
            projectId = "",
            credentialsPath = "pathToServiceAccountJsonFile",
            receivingAppIds = setOf("project-id.appOne.firebase"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail validation without or blank credentials") {
        val subject =
          AndroidPushConfig(
            enabled = true,
            projectId = "project-id",
            credentialsPath = "",
            receivingAppIds = setOf("project-id.appOne.firebase"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail validation without or blank projectId and credentials") {
        val subject =
          AndroidPushConfig(
            enabled = true,
            projectId = "",
            credentialsPath = "",
            receivingAppIds = setOf("project-id.appOne.firebase"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }
    }
  })
