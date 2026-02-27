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

import arrow.core.Either
import arrow.core.raise.either
import de.gematik.ti.pushgateway.failure.CouldNotDerivePushProvider
import de.gematik.ti.pushgateway.failure.PushPayloadFailure
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.Device

val Device.pushProvider: Either<PushPayloadFailure, PushProvider>
  get() = PushProvider.from(device = this)

val Device.pushClientIdentifier: Either<PushPayloadFailure, PushClientIdentifier>
  get() =
    pushProvider
      .mapLeft { CouldNotDerivePushProvider(device = this) }
      .map { p ->
        when (p) {
          PushProvider.IOS -> PushClientIdentifier(appId.substringBeforeLast(delimiter = "."))
          PushProvider.ANDROID -> PushClientIdentifier(appId.substringBefore(delimiter = "."))
        }
      }

val Device.pushClientInfo: Either<PushPayloadFailure, PushClientInfo>
  get() = either {
    PushClientInfo(
      appId = appId,
      pushKey = pushkey,
      pushClientIdentifier = pushClientIdentifier.bind(),
      pushProvider = PushProvider.from(device = this@pushClientInfo).bind(),
    )
  }
