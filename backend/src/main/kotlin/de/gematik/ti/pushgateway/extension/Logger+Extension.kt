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
import de.gematik.ti.pushgateway.failure.GeneralFailure
import org.slf4j.Logger
import org.slf4j.event.Level

fun <L : GeneralFailure, R> Logger.log(
  either: Either<L, R>,
  successMessageBuilder: ((R) -> Pair<String, Level?>)? = null,
) {

  either.fold(
    ifLeft = { failure ->
      /**
       * The idea is that any failure which has an exception, provides a message with exactly one
       * placeholder like so: message = "Something happened: {}" The placeholder is used for
       * exception.message. Additionally, in order to log a stacktrace the exception itself is given
       * to the logger as the last argument.
       *
       * If a failure has no exception the message is simply a message without placeholders.
       */
      val logEventBuilder = this.atLevel(failure.logLevel)
      failure.exception?.let {
        logEventBuilder.log(failure.message, it.message ?: "no details available", it)
      } ?: logEventBuilder.log(failure.message)
    },
    ifRight = { success ->
      successMessageBuilder?.let {
        val messageAndLogLevel = it(success)
        this.atLevel(messageAndLogLevel.second ?: Level.DEBUG).log(messageAndLogLevel.first)
      }
    },
  )
}
