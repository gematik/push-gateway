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

package de.gematik.ti.pushgateway.extension

import de.gematik.ti.pushgateway.failure.CouldNotDerivePushProvider
import de.gematik.ti.pushgateway.failure.CouldNotIdentifyPushProvider
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.Device
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec

class DeviceExtensionTest :
  ShouldSpec({
    context("Device.pushProvider") {
      should("convert to right pushProvider with .apns ending") {
        val subject = Device(appId = "app.id.apns", pushkey = "push.key")

        subject.pushProvider shouldBeRight PushProvider.IOS
      }

      should("convert to right pushProvider with .firebase ending") {
        val subject = Device(appId = "project-id.app.id.firebase", pushkey = "push.key")

        subject.pushProvider shouldBeRight PushProvider.ANDROID
      }

      should("fail to convert to left with .invalid ending") {
        val subject = Device(appId = "project-id.app.id.invalid", pushkey = "push.key")

        subject.pushProvider shouldBeLeft CouldNotIdentifyPushProvider(device = subject)
      }
    }

    context("Device.pushClientInfo") {
      should("convert to right pushClientInfo with .apns ending") {
        val subject = Device(appId = "app.id.apns", pushkey = "push.key")

        subject.pushClientInfo shouldBeRight
          PushClientInfo(
            appId = "app.id.apns",
            pushKey = "push.key",
            pushClientIdentifier = PushClientIdentifier("app.id"),
            pushProvider = PushProvider.IOS,
          )
      }

      should("convert to right pushClientInfo with .firebase ending") {
        val subject = Device(appId = "project-id.app.id.firebase", pushkey = "push.key")

        subject.pushClientInfo shouldBeRight
          PushClientInfo(
            appId = "project-id.app.id.firebase",
            pushKey = "push.key",
            pushClientIdentifier = PushClientIdentifier("project-id"),
            pushProvider = PushProvider.ANDROID,
          )
      }

      should("fail to convert to left with .invalid ending") {
        val subject = Device(appId = "project-id.app.id.invalid", pushkey = "push.key")

        subject.pushClientInfo shouldBeLeft CouldNotDerivePushProvider(device = subject)
      }
    }

    context("Device.pushClientIdentifier") {
      should("convert to right pushClientIdentifier with .apns ending") {
        val subject = Device(appId = "app.id.apns", pushkey = "push.key")

        subject.pushClientIdentifier shouldBeRight PushClientIdentifier("app.id")
      }

      should("convert to right pushClientIdentifier with .firebase ending") {
        val subject = Device(appId = "project-id.app.id.firebase", pushkey = "push.key")

        subject.pushClientIdentifier shouldBeRight PushClientIdentifier("project-id")
      }

      should("convert to left with .invalid ending") {
        val subject = Device(appId = "project-id.app.id.invalid", pushkey = "push.key")

        subject.pushClientIdentifier shouldBeLeft CouldNotDerivePushProvider(device = subject)
      }
    }
  })
