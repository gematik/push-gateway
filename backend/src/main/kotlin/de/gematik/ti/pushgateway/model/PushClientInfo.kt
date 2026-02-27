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

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * appId: this identifies the app in the target push provider, e.g. bundleId in Apns pushKey: this
 * identifies the device that the push notification is to be delivered to
 */
data class PushClientInfo(
  @get:JsonProperty("appId", required = true) val appId: String,
  @get:JsonProperty("pushKey", required = true) val pushKey: String,
  @get:JsonProperty("pushClientIdentifier", required = true)
  val pushClientIdentifier: PushClientIdentifier,
  @get:JsonProperty("pushProvider", required = true) val pushProvider: PushProvider,
)
