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
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.recover
import arrow.core.right
import de.gematik.ti.pushgateway.failure.CouldNotDeregisterAllDesiredPushkeys
import de.gematik.ti.pushgateway.failure.FailedToAddPushKey
import de.gematik.ti.pushgateway.failure.PushKeyNotFound
import de.gematik.ti.pushgateway.failure.RejectedPushKeyFailure
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyEntity
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyRepository
import org.slf4j.Logger
import org.springframework.stereotype.Service

@Service
class RejectedPushKeyService(
  private val logger: Logger,
  private val rejectedPushKeyRepository: RejectedPushKeyRepository,
) {
  fun findAndRemoveRejectedPushKeys(pushkeys: List<String>): Set<String> {

    val rejectedPushKeys = findRejectedPushKeyEntities(pushkeys)
    removeRejectedPushKeys(rejectedPushKeys)

    return rejectedPushKeys.map { it.pushKey }.toSet()
  }

  fun findRejectedPushKeyEntities(pushKeys: List<String>): List<RejectedPushKeyEntity> {
    logger.debug("find previously rejected pushKeys")
    return rejectedPushKeyRepository.findAllById(pushKeys)
  }

  fun removeRejectedPushKeys(pushKeyEntities: List<RejectedPushKeyEntity>) {
    logger.debug("remove RejectedPushKeys: {}", pushKeyEntities)
    rejectedPushKeyRepository.deleteAll(pushKeyEntities)
  }

  fun removeAllRejectedPushKeys(pushkeys: Set<String>): Either<RejectedPushKeyFailure, Int> =
    either {
      val deleted = rejectedPushKeyRepository.deleteByPushKeyIn(pushkeys)
      ensure(deleted == pushkeys.size) { CouldNotDeregisterAllDesiredPushkeys(pushkeys = pushkeys) }

      deleted
    }

  fun addRejectedPushKey(
    rejectedPushKeyEntity: RejectedPushKeyEntity
  ): Either<FailedToAddPushKey, Unit> =
    getByPushKey(rejectedPushKeyEntity.pushKey)
      .map {}
      .recover {
        Either.catch {
            logger.debug("save rejectedPushKey: {}", it)
            rejectedPushKeyRepository.save(rejectedPushKeyEntity)
          }
          .mapLeft { throwable -> FailedToAddPushKey(rejectedPushKeyEntity.pushKey, throwable) }
          .bind()
      }

  fun getByPushKey(pushKey: String): Either<PushKeyNotFound, RejectedPushKeyEntity> {
    return rejectedPushKeyRepository.findByPushKey(pushKey)?.right()
      ?: PushKeyNotFound(pushKey).left()
  }
}
