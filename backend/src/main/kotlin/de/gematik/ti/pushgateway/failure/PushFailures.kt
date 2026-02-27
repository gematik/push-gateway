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

import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.Device

sealed interface PushConversionFailure : GeneralFailure

sealed interface PushConfigFailure : PushConversionFailure

sealed interface PushPayloadFailure : PushConversionFailure

data class CouldNotFindPushClient(val device: Device, val pushClientInfo: PushClientInfo) :
  PushConfigFailure {
  override val message: String
    get() {
      val pushProvider =
        when (pushClientInfo.pushProvider) {
          PushProvider.IOS -> "APNS"
          PushProvider.ANDROID -> "FIREBASE"
        }
      return "[$pushProvider] Could not find push client with pushClientIdentifier=${pushClientInfo.pushClientIdentifier.value}"
    }
}

data class PushkeyHasBeenRejectedPreviously(val pushClientInfo: PushClientInfo) :
  PushPayloadFailure {
  override val message: String
    get() {
      val pushProvider =
        when (pushClientInfo.pushProvider) {
          PushProvider.IOS -> "APNS"
          PushProvider.ANDROID -> "FIREBASE"
        }
      return "[$pushProvider] The push key ${pushClientInfo.pushKey} has been rejected previously."
    }
}

data class CouldNotDerivePushProvider(val device: Device) : PushPayloadFailure {
  override val message: String
    get() = "Could not derive push provider from appId=${device.appId}"
}

data class CouldNotIdentifyPushProvider(val device: Device) : PushPayloadFailure {
  override val message: String
    get() = "Could not identify push provider for device with appId=${device.appId}"
}
