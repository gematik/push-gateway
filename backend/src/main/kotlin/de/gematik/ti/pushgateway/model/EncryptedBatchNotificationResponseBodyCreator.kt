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

import de.gematik.ti.pushgateway.failure.PushConversionFailure
import de.gematik.ti.pushgateway.failure.PushkeyHasBeenRejectedPreviously
import de.gematik.ti.pushgateway.openapi.model.BatchNotificationResponse
import de.gematik.ti.pushgateway.openapi.model.BatchNotificationResponseResultsInner
import de.gematik.ti.pushgateway.openapi.model.BatchNotificationResponseSummary
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import io.grpc.InternalChannelz.id

object EncryptedBatchNotificationResponseBodyCreator {

  fun from(
    validated: Map<String, EncryptedNotification>,
    failures: Map<String, PushConversionFailure>,
  ): BatchNotificationResponse {
    val success =
      validated.map { (id, _) ->
        BatchNotificationResponseResultsInner(
          id = id,
          status = BatchNotificationResponseResultsInner.Status.SUCCESS,
          rejected = emptyList(),
          error = null,
        )
      }

    val failed =
      failures.map { (id, failure) ->
        BatchNotificationResponseResultsInner(
          id = id,
          status = BatchNotificationResponseResultsInner.Status.FAILED,
          rejected =
            if (failure is PushkeyHasBeenRejectedPreviously) {
              listOf(failure.pushClientInfo.pushKey)
            } else {
              emptyList()
            },
          error = failure.message,
        )
      }

    val all = (success + failed).sortedBy { it.id }
    return BatchNotificationResponse(
      results = all,
      summary =
        BatchNotificationResponseSummary(
          total = all.size,
          successful = success.size,
          failed = failed.size,
          partial = 0,
        ),
    )
  }
}
