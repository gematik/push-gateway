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

import de.gematik.ti.pushgateway.config.IosPushConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class IosPushConfigValidatorTest :
  ShouldSpec({
    val sut = IosPushConfigValidator()

    context("pass validation") {
      should("pass with authMethod = TOKEN") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.TOKEN,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = IosPushConfig.TokenAuth(token = "token", keyId = "keyId", teamId = "teamId"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe true
      }

      should("pass with authMethod = CERTIFICATE") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.CERTIFICATE,
            bundleId = "valid.bundleId",
            certificateAuth =
              IosPushConfig.CertificateAuth(
                certificatePath = "pathToP12File",
                passwordPath = "secret",
              ),
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = null,
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe true
      }

      should("pass when disabled") {
        val subject = IosPushConfig(enabled = false, receivingAppIds = emptySet())

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe true
      }
    }

    context("fail validation") {
      should("fail when authMethod = TOKEN but no tokenAuth is defined") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.TOKEN,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = null,
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail when authMethod = TOKEN but tokenAuth is missing token") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.TOKEN,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = IosPushConfig.TokenAuth(token = "", keyId = "keyId", teamId = "teamId"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail when authMethod = TOKEN but tokenAuth is missing keyId") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.TOKEN,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = IosPushConfig.TokenAuth(token = "token", keyId = "", teamId = "teamId"),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail when authMethod = TOKEN but tokenAuth is missing teamId") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.TOKEN,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = IosPushConfig.TokenAuth(token = "token", keyId = "keyId", teamId = ""),
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail when authMethod = CERTIFICATE but no certificateAuth is defined") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.CERTIFICATE,
            bundleId = "valid.bundleId",
            certificateAuth = null,
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = null,
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }

      should("fail when authMethod = CERTIFICATE but certificateAuth.certificatePath is blank") {
        val subject =
          IosPushConfig(
            authMethod = IosPushConfig.AuthMethod.CERTIFICATE,
            bundleId = "valid.bundleId",
            certificateAuth =
              IosPushConfig.CertificateAuth(certificatePath = "", passwordPath = "secret"),
            enabled = true,
            receivingAppIds = setOf("valid.appId.one.apns"),
            server = "apns.server",
            tokenAuth = null,
          )

        sut.isValid(pushConfig = subject, context = mockk(relaxed = true)) shouldBe false
      }
    }
  })
