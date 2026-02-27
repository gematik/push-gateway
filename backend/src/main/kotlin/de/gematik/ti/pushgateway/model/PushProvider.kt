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

package de.gematik.ti.pushgateway.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.fasterxml.jackson.annotation.JsonProperty
import de.gematik.ti.pushgateway.failure.CouldNotIdentifyPushProvider
import de.gematik.ti.pushgateway.failure.PushPayloadFailure
import de.gematik.ti.pushgateway.openapi.model.Device

enum class PushProvider(@get:JsonProperty("suffix", required = true) val suffix: String) {
  ANDROID("firebase"),
  IOS("apns");

  companion object {
    fun from(device: Device): Either<PushPayloadFailure, PushProvider> = either {
      val identifierCandidate = device.appId.split(".").last()
      ensureNotNull(entries.find { it.suffix == identifierCandidate }) {
        CouldNotIdentifyPushProvider(device = device)
      }
    }
  }
}
