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

package de.gematik.ti.pushgateway.validation

import de.gematik.ti.pushgateway.config.IosPushConfig
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IosPushConfigValidator::class])
annotation class ValidIosPushConfig(
  val message: String = "Invalid APNs configuration",
  val groups: Array<KClass<out Any>> = [],
  val payload: Array<KClass<out Any>> = [],
)

class IosPushConfigValidator : ConstraintValidator<ValidIosPushConfig, IosPushConfig> {

  override fun isValid(pushConfig: IosPushConfig, context: ConstraintValidatorContext): Boolean =
    if (pushConfig.enabled) {
      when (pushConfig.authMethod) {
        IosPushConfig.AuthMethod.TOKEN -> {
          val tokenAuth = pushConfig.tokenAuth
          tokenAuth != null &&
            tokenAuth.token.isNotBlank() &&
            tokenAuth.teamId.isNotBlank() &&
            tokenAuth.keyId.isNotBlank()
        }

        IosPushConfig.AuthMethod.CERTIFICATE -> {
          val certAuth = pushConfig.certificateAuth
          certAuth != null && certAuth.certificatePath.isNotBlank()
        }
      }
    } else {
      true
    }
}
