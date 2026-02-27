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
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.eatthepath.pushy.apns.ApnsClient
import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.failure.CouldNotFindApnsClient
import de.gematik.ti.pushgateway.failure.CouldNotFindFirebaseClient
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import org.slf4j.Logger
import org.springframework.stereotype.Component

@Component
class PushClientProvider(
  private val logger: Logger,
  val apnsClients: Map<PushClientIdentifier, ApnsClient>,
  val firebaseClients: Map<PushClientIdentifier, FirebaseMessaging>,
) {

  fun apnsClientFor(bundleId: PushClientIdentifier): Either<CouldNotFindApnsClient, ApnsClient> =
    either {
      ensureNotNull(apnsClients[bundleId]) { CouldNotFindApnsClient(bundleId = bundleId) }
    }

  fun hasApnsClientFor(bundleId: PushClientIdentifier): Boolean = apnsClients.containsKey(bundleId)

  fun firebaseClientFor(
    projectId: PushClientIdentifier
  ): Either<CouldNotFindFirebaseClient, FirebaseMessaging> = either {
    ensureNotNull(firebaseClients[projectId]) { CouldNotFindFirebaseClient(projectId = projectId) }
  }

  fun hasFirebaseClientFor(projectId: PushClientIdentifier): Boolean =
    firebaseClients.containsKey(projectId)
}
