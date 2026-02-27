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

package de.gematik.ti.pushgateway.metrics.aop

import arrow.core.Either
import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.application
import io.micrometer.core.annotation.Counted
import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.context.annotation.Conditional
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * The default @Counted annotation works exception based which is why it cannot be used sensibly in
 * either-based environments because we simply don't have control flow breaking exceptions there.
 *
 * Furthermore, we want to have on metric per firebase client which can be achieved by adding
 * firebase details to it.
 */
private const val NOT_SPECIFIED = "not specified"

@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
@Conditional(ConsumerCondition::class)
class FunctionalCountedFirebaseSendAspect(private val meterRegistry: MeterRegistry) {

  @Around("@annotation(counted) && args(firebaseClient,..)")
  fun around(point: ProceedingJoinPoint, counted: Counted, firebaseClient: FirebaseMessaging): Any {
    val projectId = firebaseClient.application.options.projectId
    val successCounter = meterRegistry.counter("${counted.value}_accepted", "projectId", projectId)

    val result =
      try {
        point.proceed()
      } catch (ex: Throwable) {
        meterRegistry
          .counter(
            "${counted.value}_failed",
            "projectId",
            projectId,
            "reason",
            ex.message ?: NOT_SPECIFIED,
          )
          .apply { increment() }
        throw ex
      }

    when (result) {
      is Either<*, *> -> {
        if (result.isRight()) {
          successCounter.increment()
        } else {
          meterRegistry
            .counter(
              "${counted.value}_failed",
              "projectId",
              projectId,
              "reason",
              result.leftOrNull()?.javaClass?.simpleName ?: NOT_SPECIFIED,
            )
            .apply { increment() }
        }
      }
      is Result<*> -> {
        if (result.isSuccess) {
          successCounter.increment()
        } else {
          meterRegistry
            .counter(
              "${counted.value}_failed",
              "projectId",
              projectId,
              "reason",
              result.exceptionOrNull()?.message ?: NOT_SPECIFIED,
            )
            .apply { increment() }
        }
      }
      else -> {
        successCounter.increment()
      }
    }

    return result
  }
}
