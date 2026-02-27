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

import de.gematik.ti.pushgateway.mock.PushServiceMocks
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ApplyExtension(SpringExtension::class)
@Import(PushServiceMocks::class)
@AutoConfigureEmbeddedDatabase
@TestPropertySource(
  properties = ["spring.artemis.embedded.persistent=false", "logging.level.root=INFO"]
)
@ActiveProfiles("integrationtest")
class AppConfigIT(
  val jmsConfig: JmsConfig,
  val loggerConfig: LoggerConfig,
  val openApiConfig: OpenApiConfig,
  val pushConfig: PushConfig,
) :
  ShouldSpec({
    context("pushConfig") {
      context("apns") {
        should("load apns configuration") {
          pushConfig.pushConfigs.apns shouldHaveSize 2
          pushConfig.pushConfigs.apns
            .first { it.bundleId == "com.gematik.apps.alpha" }
            .apply {
              enabled shouldBe true
              server shouldBe "api.sandbox.push.apple.com"
              bundleId shouldBe "com.gematik.apps.alpha"
              authMethod shouldBe IosPushConfig.AuthMethod.TOKEN
              certificateAuth shouldBe null
              tokenAuth shouldNotBe null
              tokenAuth?.token shouldBe "classpath:/app/config/apns/com.gematik.apps.alpha.apns.p8"
              tokenAuth?.keyId shouldBe "keyId"
              tokenAuth?.teamId shouldBe "teamId"
              receivingAppIds shouldHaveSize 1
              receivingAppIds.first() shouldBe "com.gematik.apps.alpha.apns"
            }
          pushConfig.pushConfigs.apns
            .first { it.bundleId == "com.gematik.apps.beta" }
            .apply {
              enabled shouldBe true
              server shouldBe "api.sandbox.push.apple.com"
              bundleId shouldBe "com.gematik.apps.beta"
              authMethod shouldBe IosPushConfig.AuthMethod.CERTIFICATE
              tokenAuth shouldBe null
              certificateAuth shouldNotBe null
              certificateAuth?.certificatePath shouldBe
                "classpath:/app/config/apns/com.gematik.apps.beta.apns.p12"
              certificateAuth?.passwordPath shouldBe
                "classpath:/app/config/apns/com.gematik.apps.beta.apns.p12.password"
              receivingAppIds shouldHaveSize 1
              receivingAppIds.first() shouldBe "com.gematik.apps.beta.apns"
            }
        }
      }

      context("firebase") {
        should("load firebase configuration") {
          pushConfig.pushConfigs.firebase shouldHaveSize 2
          pushConfig.pushConfigs.firebase
            .first { it.projectId == "gematik-project-alpha" }
            .apply {
              enabled shouldBe true
              credentialsPath shouldBe
                "classpath:/app/config/firebase/gematik-project-alpha.service-account.json"
              receivingAppIds shouldHaveSize 2
              receivingAppIds shouldContainExactlyInAnyOrder
                setOf(
                  "gematik-project-alpha.app-one.firebase",
                  "gematik-project-alpha.app-two.firebase",
                )
            }
          pushConfig.pushConfigs.firebase
            .first { it.projectId == "gematik-project-beta" }
            .apply {
              enabled shouldBe true
              credentialsPath shouldBe
                "classpath:/app/config/firebase/gematik-project-beta.service-account.json"
              receivingAppIds shouldHaveSize 1
              receivingAppIds.first() shouldBe "gematik-project-beta.app.one.firebase"
            }
        }
      }
    }

    context("jmsConfig") { should("activate jms config") { jmsConfig shouldNotBe null } }

    context("loggerConfig") { should("activate logger config") { loggerConfig shouldNotBe null } }

    context("openApiConfig") {
      should("actiavte open api config") { openApiConfig shouldNotBe null }
    }
  })
