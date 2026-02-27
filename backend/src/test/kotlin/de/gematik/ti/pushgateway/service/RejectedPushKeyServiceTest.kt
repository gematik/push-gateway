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
import de.gematik.ti.pushgateway.failure.CouldNotDeregisterAllDesiredPushkeys
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyEntity
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import org.slf4j.Logger

class RejectedPushKeyServiceTest :
  DescribeSpec({
    mockkStatic(Logger::class)

    val logger = mockk<Logger>(relaxed = true)
    val repoMock = mockk<RejectedPushKeyRepository>()
    val objectUnderTest = RejectedPushKeyService(logger, repoMock)

    val rejectedPushKeyEntity1 =
      RejectedPushKeyEntity(pushKey = "pushKey1", appId = "appId1", reason = "reason1")

    val rejectedPushKeyEntity2 =
      RejectedPushKeyEntity(pushKey = "pushKey2", appId = "appId2", reason = "reason2")

    val rejectedPushKeys = listOf(rejectedPushKeyEntity1.pushKey, rejectedPushKeyEntity2.pushKey)

    beforeTest { clearAllMocks() }

    context("findAndRemovePushKey") {
      it("should find previously rejected push keys and delete them") {
        every { repoMock.findAllById(rejectedPushKeys) } returns
          listOf(rejectedPushKeyEntity1, rejectedPushKeyEntity2)
        every { repoMock.deleteAll(listOf(rejectedPushKeyEntity1, rejectedPushKeyEntity2)) } just
          Runs

        val result = objectUnderTest.findAndRemoveRejectedPushKeys(rejectedPushKeys)
        result.shouldContainExactly(rejectedPushKeys)
        verify(exactly = 1) {
          repoMock.deleteAll(listOf(rejectedPushKeyEntity1, rejectedPushKeyEntity2))
        }
      }
      it("should not find previously rejected push keys and do nothing") {
        every { repoMock.findAllById(rejectedPushKeys) } returns emptyList()
        every { repoMock.deleteAll(emptyList()) } just Runs

        val result = objectUnderTest.findAndRemoveRejectedPushKeys(rejectedPushKeys)
        result.isEmpty()
        verify(exactly = 1) { repoMock.deleteAll(emptyList()) }
      }
      it("should find one previously rejected push key and delete it") {
        every { repoMock.findAllById(rejectedPushKeys) } returns listOf(rejectedPushKeyEntity1)
        every { repoMock.deleteAll(listOf(rejectedPushKeyEntity1)) } just Runs

        val result = objectUnderTest.findAndRemoveRejectedPushKeys(rejectedPushKeys)
        result.shouldContainExactly(rejectedPushKeyEntity1.pushKey)
        verify(exactly = 1) { repoMock.deleteAll(listOf(rejectedPushKeyEntity1)) }
      }
    }

    context("addRejectedPushKey") {
      it("should add a pushKey successfully") {
        every { repoMock.findByPushKey(any()) } returns null
        every { repoMock.save(any()) } returns mockk()

        objectUnderTest.addRejectedPushKey(rejectedPushKeyEntity1) shouldBeRight Unit
      }

      it("should fail adding a pushKey because it already exists") {
        every { repoMock.findByPushKey(any()) } returns rejectedPushKeyEntity1

        objectUnderTest.addRejectedPushKey(rejectedPushKeyEntity1) shouldBeRight Unit
      }

      it("should fail adding a pushKey because of exception") {
        every { repoMock.findByPushKey(any()) } returns null
        every { repoMock.save(any()) } throws RuntimeException()
      }
      objectUnderTest
        .addRejectedPushKey(rejectedPushKeyEntity1)
        .shouldBeInstanceOf<Either.Left<Unit>>()
    }

    context("removeAllRejectedPushKeys") {
      val rejectedPushKeys = setOf("one", "two", "three")
      it("should remove all rejected push keys") {
        every { repoMock.deleteByPushKeyIn(rejectedPushKeys) } returns rejectedPushKeys.size

        objectUnderTest.removeAllRejectedPushKeys(rejectedPushKeys) shouldBeRight 3
      }

      it("should report if some or all pushkeys could not be deleted") {
        every { repoMock.deleteByPushKeyIn(rejectedPushKeys) } returns rejectedPushKeys.size - 1

        objectUnderTest.removeAllRejectedPushKeys(rejectedPushKeys) shouldBeLeft
          CouldNotDeregisterAllDesiredPushkeys(rejectedPushKeys)
      }
    }
  })
