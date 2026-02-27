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

package de.gematik.ti.pushgateway.config

import arrow.core.Either
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.eatthepath.pushy.apns.ApnsClient
import com.eatthepath.pushy.apns.ApnsClientBuilder
import com.eatthepath.pushy.apns.auth.ApnsSigningKey
import com.eatthepath.pushy.apns.metrics.micrometer.MicrometerApnsClientMetricsListener
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import de.gematik.ti.pushgateway.extension.log
import de.gematik.ti.pushgateway.failure.*
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.validation.ValidAndroidPushConfig
import de.gematik.ti.pushgateway.validation.ValidIosPushConfig
import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.codec.http2.Http2FrameLogger
import io.netty.handler.logging.LogLevel
import jakarta.validation.constraints.NotBlank
import org.slf4j.Logger
import org.slf4j.event.Level
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.validation.annotation.Validated

@Configuration
class PushConfig(
  val logger: Logger,
  val resourceLoader: ResourceLoader,
  val meterRegistry: MeterRegistry,
  val pushConfigs: PushConfigurations,
) {

  @Bean
  fun apnsClients(): Map<PushClientIdentifier, ApnsClient> =
    pushConfigs.apns
      .filter { it.enabled }
      .mapNotNull { config ->
        config
          .toClient(resourceLoader, meterRegistry)
          .also {
            logger.log(it) {
              "[APNS] Created client for bundleId=${config.bundleId} with authMethod=${config.authMethod.name}" to
                Level.INFO
            }
          }
          .map { Pair(config, it) }
          .mapLeft { null }
          .merge()
      }
      .associate { pair -> PushClientIdentifier(pair.first.bundleId) to pair.second }

  @Bean
  fun firebaseClients(): Map<PushClientIdentifier, FirebaseMessaging> =
    pushConfigs.firebase
      .filter { it.enabled }
      .mapNotNull { config ->
        config
          .toClient(resourceLoader)
          .also {
            logger.log(it) {
              "[FIREBASE] Created client for projectId=${config.projectId}" to Level.INFO
            }
          }
          .map { Pair(config, it) }
          .mapLeft { null }
          .merge()
      }
      .associate { pair -> PushClientIdentifier(pair.first.projectId) to pair.second }
}

@ConfigurationProperties(prefix = "push")
data class PushConfigurations(val apns: List<IosPushConfig>, val firebase: List<AndroidPushConfig>)

@Validated
@ValidAndroidPushConfig
data class AndroidPushConfig(
  val enabled: Boolean,
  val projectId: String,
  val credentialsPath: String,
  val receivingAppIds: Set<String>,
) {
  fun toClient(resourceLoader: ResourceLoader): Either<FirebaseConfigFailure, FirebaseMessaging> =
    either {
      val json = resourceLoader.getResource(credentialsPath)
      ensure(json.exists() && json.isReadable) {
        CouldNotReadFirebaseCredentials(credentialsPath = credentialsPath)
      }
      val options =
        json.inputStream.use {
          FirebaseOptions.builder()
            .setProjectId(projectId)
            .setCredentials(GoogleCredentials.fromStream(it))
            .build()
        }

      val app =
        FirebaseApp.getApps().firstOrNull { it.name == projectId }
          ?: FirebaseApp.initializeApp(options, projectId)

      FirebaseMessaging.getInstance(app)
    }
}

@Validated
@ValidIosPushConfig
data class IosPushConfig(
  val enabled: Boolean,
  @NotBlank val server: String = "",
  @NotBlank val bundleId: String = "",
  val authMethod: AuthMethod = AuthMethod.TOKEN,
  val tokenAuth: TokenAuth? = null,
  val certificateAuth: CertificateAuth? = null,
  val receivingAppIds: Set<String>,
) {

  enum class AuthMethod {
    TOKEN,
    CERTIFICATE,
  }

  data class TokenAuth(val token: String = "", val keyId: String = "", val teamId: String = "")

  data class CertificateAuth(val certificatePath: String = "", val passwordPath: String? = "")

  fun toClient(
    resourceLoader: ResourceLoader,
    meterRegistry: MeterRegistry,
  ): Either<APNSConfigFailure, ApnsClient> = either {
    ApnsClientBuilder()
      .setApnsServer(server)
      .setMetricsListener(
        MicrometerApnsClientMetricsListener(
          meterRegistry,
          "bundleId",
          bundleId,
          "authMethod",
          authMethod.name,
        )
      )
      .apply {
        setFrameLogger(Http2FrameLogger(LogLevel.DEBUG, "APNS_HTTP2"))

        when (authMethod) {
          AuthMethod.TOKEN ->
            ensureNotNull(tokenAuth) { ApnsTokenConfigNotProvided(bundleId = bundleId) }
              .apply {
                val p8 = resourceLoader.getResource(token)
                ensure(p8.exists() && p8.isReadable) { CouldNotReadApnsToken(tokenPath = token) }
                setSigningKey(ApnsSigningKey.loadFromPkcs8File(p8.file, teamId, keyId))
              }

          AuthMethod.CERTIFICATE -> {
            ensureNotNull(certificateAuth) { ApnsCertificateConfigNotProvided(bundleId = bundleId) }
              .apply {
                val p12 = resourceLoader.getResource(certificatePath)
                ensure(p12.exists() && p12.isReadable) {
                  CouldNotReadApnsCertificate(certificatePath = certificatePath)
                }
                val p12Password =
                  passwordPath?.let { passwordFile ->
                    resourceLoader
                      .getResource(passwordFile)
                      .takeIf { it.exists() && it.isReadable }
                      ?.getContentAsString(Charsets.UTF_8) ?: ""
                  } ?: ""
                setClientCredentials(p12.file, p12Password)
              }
          }
        }
      }
      .build()
  }
}
