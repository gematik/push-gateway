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

import arrow.core.left
import arrow.core.right
import de.gematik.ti.pushgateway.failure.GeneralFailure
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.slf4j.Logger
import org.slf4j.event.Level

class LoggerExtensionTest :
  ShouldSpec({
    val logger = mockk<Logger>(relaxed = true)

    beforeTest { clearMocks(logger) }

    context("left") {
      withData(Level.entries) { level ->
        should("log left without exception") {
          val error =
            object : GeneralFailure {
              override val message: String = "Something went wrong"
              override val logLevel: Level = level
            }

          val either = error.left()

          shouldNotThrowAny { logger.log(either) }

          verify { logger.atLevel(level).log(error.message) }
        }

        should("log left with exception at level $level") {
          val exception = RuntimeException("Boom")
          val error =
            object : GeneralFailure {
              override val message: String = "Something went wrong"
              override val exception: Throwable = exception
              override val logLevel: Level = level
            }

          val either = error.left()

          shouldNotThrowAny { logger.log(either) }

          verify { logger.atLevel(level).log(error.message, "Boom", exception) }
        }
      }
    }

    context("right") {
      withData(Level.entries + null) { level ->
        should("log right with message at level ${level ?: "default"}") {
          val either = "All good".right()

          val messageBuilder: (String) -> Pair<String, Level?> = { value ->
            "Success: $value" to level
          }

          shouldNotThrowAny { logger.log(either, messageBuilder) }

          verify { (level ?: Level.DEBUG).let { logger.atLevel(it).log("Success: All good") } }
        }
      }

      should("not log right with not messageBuilder present") {
        val either = "All good".right()

        shouldNotThrowAny { logger.log(either) }

        verify(exactly = 0) { logger.atLevel(any()).log(any<String>()) }
      }
    }
  })
