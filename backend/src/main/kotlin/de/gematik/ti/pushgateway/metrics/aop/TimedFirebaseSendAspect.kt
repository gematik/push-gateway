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

import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.config.ConsumerCondition
import de.gematik.ti.pushgateway.extension.application
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

/**
 * We want to have one metric per firebase client which can be achieved by inserting the firebase
 * projectId to the timer's tags.
 */
@Aspect
@Component
@Conditional(ConsumerCondition::class)
class TimedFirebaseSendAspect(private val meterRegistry: MeterRegistry) {

  @Around("@annotation(timed) && args(firebaseClient,..)")
  fun around(point: ProceedingJoinPoint, timed: Timed, firebaseClient: FirebaseMessaging): Any {
    val projectId = firebaseClient.application.options.projectId
    val timer = meterRegistry.timer(timed.value, "projectId", projectId)
    val sample = Timer.start(meterRegistry)

    return try {
      point.proceed()
    } finally {
      sample.stop(timer)
    }
  }
}
