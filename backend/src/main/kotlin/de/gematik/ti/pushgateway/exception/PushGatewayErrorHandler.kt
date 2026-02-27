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

package de.gematik.ti.pushgateway.exception

import de.gematik.ti.pushgateway.openapi.model.PushV1NotifyBatchEncrypted400Response
import de.gematik.ti.pushgateway.openapi.model.PushV1NotifyBatchPlain400Response
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Global Error Handler mapping all exceptions to the OpenAPI-specified error model */
@RestControllerAdvice
class PushGatewayErrorHandler {

  @ExceptionHandler(InvalidEncryptedBatchRequestException::class)
  fun handleEncryptedBatchException(
    ex: InvalidEncryptedBatchRequestException
  ): ResponseEntity<PushV1NotifyBatchEncrypted400Response> {
    return ResponseEntity(
      PushV1NotifyBatchEncrypted400Response(
        error = ex.error, // OpenAPI Enum
        details = ex.details,
      ),
      HttpStatus.BAD_REQUEST,
    )
  }

  @ExceptionHandler(InvalidPlainBatchRequestException::class)
  fun handlePlainBatchException(
    ex: InvalidPlainBatchRequestException
  ): ResponseEntity<PushV1NotifyBatchPlain400Response> {
    return ResponseEntity(
      PushV1NotifyBatchPlain400Response(
        error = ex.error, // OpenAPI Enum
        details = ex.details,
      ),
      HttpStatus.BAD_REQUEST,
    )
  }
}
