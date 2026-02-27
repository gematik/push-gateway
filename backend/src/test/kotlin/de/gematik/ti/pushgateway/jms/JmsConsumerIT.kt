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

package de.gematik.ti.pushgateway.jms

import com.ninjasquad.springmockk.MockkSpyBean
import de.gematik.ti.pushgateway.jms.ApnsJmsConsumer.Companion.QUEUE_APNS_ENCRYPTED
import de.gematik.ti.pushgateway.jms.ApnsJmsConsumer.Companion.QUEUE_APNS_PLAIN
import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_ENCRYPTED
import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_PLAIN
import de.gematik.ti.pushgateway.jms.model.EncryptedNotificationMessage
import de.gematik.ti.pushgateway.jms.model.PlainNotificationMessage
import de.gematik.ti.pushgateway.mock.PushServiceMocks
import de.gematik.ti.pushgateway.model.PushClientIdentifier
import de.gematik.ti.pushgateway.model.PushClientInfo
import de.gematik.ti.pushgateway.model.PushProvider
import de.gematik.ti.pushgateway.openapi.model.Device
import de.gematik.ti.pushgateway.openapi.model.EncryptedNotification
import de.gematik.ti.pushgateway.openapi.model.PlainNotification
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.verify
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import jakarta.jms.Message
import java.util.concurrent.TimeUnit
import org.awaitility.kotlin.await
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jms.core.JmsTemplate
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
class JmsConsumerIT(private val jmsTemplate: JmsTemplate) : ShouldSpec() {

  @MockkSpyBean lateinit var firebaseJmsConsumerSpy: FirebaseJmsConsumer
  @MockkSpyBean lateinit var apnsJmsConsumerSpy: ApnsJmsConsumer

  init {
    jmsTemplate.apply { receiveTimeout = 1000L }

    context("plain notifications") {
      context("listening to messages for apns") {
        val payload =
          PlainNotificationMessage(
            notification =
              PlainNotification(eventId = "eventId", roomId = "roomId", devices = emptySet()),
            pushClientInfo =
              PushClientInfo(
                appId = "com.gematik.apps.alpha.apns",
                pushKey = "push.key",
                pushClientIdentifier = PushClientIdentifier("com.gematik.apps.alpha"),
                pushProvider = PushProvider.IOS,
              ),
            queue = QUEUE_APNS_PLAIN,
          )

        should("process plain message for apns only") {
          jmsTemplate.convertAndSend(payload.queue, payload)

          await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) {
              apnsJmsConsumerSpy.processPlainNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processPlainNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processEncryptedNotification(
                payload = any<EncryptedNotificationMessage>(),
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processEncryptedNotification(
                payload = any<EncryptedNotificationMessage>(),
                message = any<Message>(),
              )
            }
          }
        }
      }

      context("listening to messages for firebase") {
        val payload =
          PlainNotificationMessage(
            notification =
              PlainNotification(eventId = "eventId", roomId = "roomId", devices = emptySet()),
            pushClientInfo =
              PushClientInfo(
                appId = "gematik-project-alpha.com.gematik.apps.alpha.firebase",
                pushKey = "push.key",
                pushClientIdentifier = PushClientIdentifier("gematik-project-alpha"),
                pushProvider = PushProvider.ANDROID,
              ),
            queue = QUEUE_FIREBASE_PLAIN,
          )
        should("process plain message for firebase only") {
          jmsTemplate.convertAndSend(payload.queue, payload)

          await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) {
              firebaseJmsConsumerSpy.processPlainNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processPlainNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processEncryptedNotification(
                payload = any<EncryptedNotificationMessage>(),
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processEncryptedNotification(
                payload = any<EncryptedNotificationMessage>(),
                message = any<Message>(),
              )
            }
          }
        }
      }
    }

    context("encrypted notifications") {
      context("listening to messages for apns") {
        val payload =
          EncryptedNotificationMessage(
            notification =
              EncryptedNotification(
                ciphertext = "ciphertext",
                timeMessageEncrypted = "timeMessageEncrypted",
                keyIdentifier = "keyIdentifier",
                device = Device(appId = "com.gematik.apps.alpha.apns", pushkey = "my:push:key"),
              ),
            pushClientInfo =
              PushClientInfo(
                appId = "com.gematik.apps.alpha.apns",
                pushKey = "push:key",
                pushClientIdentifier = PushClientIdentifier("com.gematik.apps.alpha"),
                pushProvider = PushProvider.IOS,
              ),
            queue = QUEUE_APNS_ENCRYPTED,
          )

        should("process encrypted message for apns only") {
          jmsTemplate.convertAndSend(payload.queue, payload)
          await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) {
              apnsJmsConsumerSpy.processEncryptedNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processEncryptedNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processPlainNotification(
                payload = any<PlainNotificationMessage>(),
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processPlainNotification(
                payload = any<PlainNotificationMessage>(),
                message = any<Message>(),
              )
            }
          }
        }
      }

      context("listening to messages for firebase") {
        val payload =
          EncryptedNotificationMessage(
            notification =
              EncryptedNotification(
                ciphertext = "ciphertext",
                timeMessageEncrypted = "timeMessageEncrypted",
                keyIdentifier = "keyIdentifier",
                device =
                  Device(
                    appId = "gematik-project-alpha.com.gematik.apps.alpha.firebase",
                    pushkey = "my:push:key",
                  ),
              ),
            pushClientInfo =
              PushClientInfo(
                appId = "gematik-project-alpha.com.gematik.apps.alpha.firebase",
                pushKey = "push:key",
                pushClientIdentifier = PushClientIdentifier("gematik-project-alpha"),
                pushProvider = PushProvider.ANDROID,
              ),
            queue = QUEUE_FIREBASE_ENCRYPTED,
          )
        should("process encrypted message for firebase only") {
          jmsTemplate.convertAndSend(payload.queue, payload)

          await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) {
              firebaseJmsConsumerSpy.processEncryptedNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processEncryptedNotification(
                payload = payload,
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              apnsJmsConsumerSpy.processPlainNotification(
                payload = any<PlainNotificationMessage>(),
                message = any<Message>(),
              )
            }
            verify(exactly = 0) {
              firebaseJmsConsumerSpy.processPlainNotification(
                payload = any<PlainNotificationMessage>(),
                message = any<Message>(),
              )
            }
          }
        }
      }
    }
  }
}
