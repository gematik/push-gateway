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

package de.gematik.ti.pushgateway.api

import de.gematik.ti.pushgateway.mock.PushServiceMocks
import de.gematik.ti.pushgateway.openapi.model.*
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyEntity
import de.gematik.ti.pushgateway.persistence.RejectedPushKeyRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.extensions.spring.SpringExtension
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.hamcrest.Matchers.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ApplyExtension(SpringExtension::class)
@Import(PushServiceMocks::class)
@AutoConfigureEmbeddedDatabase
@TestPropertySource(
  properties = ["spring.artemis.embedded.persistent=false", "logging.level.root=INFO"]
)
@ActiveProfiles("integrationtest")
@AutoConfigureMockMvc
class PushGatewayApiDelegateImplIT(
  val mockMvc: MockMvc,
  val jacksonMapper: JsonMapper,
  val rejectedPushKeyRepository: RejectedPushKeyRepository,
) :
  ShouldSpec({
    val validIosDevice = Device(appId = "com.gematik.apps.alpha.apns", pushkey = "valid.pushKey")

    val validAndroidDevice =
      Device(appId = "gematik-project-alpha.app-one.firebase", pushkey = "valid.pushKey")

    val rejectedIosDevice =
      Device(appId = "com.gematik.apps.alpha.apns", pushkey = "rejected.ios.pushKey")
    val rejectedAndroidDevice =
      Device(
        appId = "gematik-project-alpha.app-one.firebase",
        pushkey = "rejected.firebase.pushKey",
      )

    // generates a valid ciphertext value, which as per spec must be exactly 1404 characters
    val validCiphertext: String = run {
      val word = "ciphertext"
      val targetLength = 1404

      val fullRepeats = targetLength / word.length
      val remainder = targetLength % word.length

      buildString {
        repeat(fullRepeats) { append(word) }
        append(word.substring(0, remainder))
      }
    }

    beforeTest {
      with(rejectedPushKeyRepository) {
        deleteAll()
        flush()
        saveAll(
          listOf(
            RejectedPushKeyEntity(
              pushKey = "rejected.ios.pushKey",
              appId = "com.gematik.apps.alpha.apns",
              reason = "REJECTED",
            ),
            RejectedPushKeyEntity(
              pushKey = "rejected.firebase.pushKey",
              appId = "gematik-project-alpha.app-one.firebase",
              reason = "REJECTED",
            ),
          )
        )
      }
    }

    context("endpoint POST /push/v1/notify") {
      should("resolve to 200 OK with proper response body") {
        val payload =
          jacksonMapper.writeValueAsString(
            PlainNotificationRequest(
              notification =
                PlainNotification(
                  devices =
                    setOf(
                      validIosDevice,
                      validAndroidDevice,
                      rejectedIosDevice,
                      rejectedAndroidDevice,
                    ),
                  eventId = "eventId",
                  roomId = "roomId",
                )
            )
          )

        mockMvc
          .post("/push/v1/notify") {
            contentType = MediaType.APPLICATION_JSON
            content = payload
          }
          .andExpect { status { isOk() } }
      }
      should("resolve to 200 OK with proper response body and only rejected") {
        val payload =
          jacksonMapper.writeValueAsString(
            PlainNotificationRequest(
              notification =
                PlainNotification(
                  devices = setOf(rejectedIosDevice, rejectedAndroidDevice),
                  eventId = "eventId",
                  roomId = "roomId",
                )
            )
          )

        mockMvc
          .post("/push/v1/notify") {
            contentType = MediaType.APPLICATION_JSON
            content = payload
          }
          .andExpect { status { isOk() } }
      }
    }

    context("endpoint POST /push/v1/notify/batch") {
      val payload =
        jacksonMapper.writeValueAsString(
          BatchNotificationRequest(
            notifications =
              setOf(
                BatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    PlainNotification(
                      devices = setOf(validIosDevice, validAndroidDevice),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
                BatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    PlainNotification(
                      devices =
                        setOf(
                          validIosDevice,
                          validAndroidDevice,
                          rejectedIosDevice,
                          rejectedAndroidDevice,
                        ),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
                BatchNotificationRequestNotificationsInner(
                  id = "id3",
                  notification =
                    PlainNotification(
                      devices = setOf(rejectedIosDevice, rejectedAndroidDevice),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
              )
          )
        )
      val payloadWithDuplicateIds =
        jacksonMapper.writeValueAsString(
          BatchNotificationRequest(
            notifications =
              setOf(
                BatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    PlainNotification(
                      devices = setOf(validIosDevice, validAndroidDevice),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
                BatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    PlainNotification(
                      devices =
                        setOf(
                          validIosDevice,
                          validAndroidDevice,
                          rejectedIosDevice,
                          rejectedAndroidDevice,
                        ),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
                BatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    PlainNotification(
                      devices = setOf(rejectedIosDevice, rejectedAndroidDevice),
                      eventId = "eventId",
                      roomId = "roomId",
                    ),
                ),
              )
          )
        )

      should("resolve to 200 OK with proper response body") {
        mockMvc
          .post("/push/v1/notify/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = payload
          }
          .andExpect { status { isOk() } }
      }

      should("resolve to 400 Bad Request with duplicate ids") {
        mockMvc
          .post("/push/v1/notify/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = payloadWithDuplicateIds
          }
          .andExpect {
            status { isBadRequest() }
            content {
              contentType(MediaType.APPLICATION_JSON)
              jsonPath("$.error") { value("Duplicate notification IDs in batch") }
              jsonPath("$.details.duplicate_ids") {
                value(hasItem("id2"))
                value(not(hasItem("id1")))
              }
            }
          }
      }
    }

    context("endpoint POST /push/v1/notifyEncrypted/batch") {
      val payload =
        jacksonMapper.writeValueAsString(
          EncryptedBatchNotificationRequest(
            notifications =
              setOf(
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validIosDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validAndroidDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id3",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = rejectedIosDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id4",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = rejectedAndroidDevice,
                    ),
                ),
              )
          )
        )

      val invalidPayload =
        jacksonMapper.writeValueAsString(
          EncryptedBatchNotificationRequest(
            notifications =
              setOf(
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    EncryptedNotification(
                      ciphertext = "tooShortCiphertext",
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validIosDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    EncryptedNotification(
                      ciphertext = "tooShortCiphertext",
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validAndroidDevice,
                    ),
                ),
              )
          )
        )

      val payloadWithDuplicateIds =
        jacksonMapper.writeValueAsString(
          EncryptedBatchNotificationRequest(
            notifications =
              setOf(
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validIosDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id1",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validAndroidDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validIosDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id3",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validAndroidDevice,
                    ),
                ),
                EncryptedBatchNotificationRequestNotificationsInner(
                  id = "id2",
                  notification =
                    EncryptedNotification(
                      ciphertext = validCiphertext,
                      timeMessageEncrypted = "timeMessageEncrypted",
                      keyIdentifier = "kid",
                      device = validAndroidDevice,
                    ),
                ),
              )
          )
        )

      should("resolve to 200 OK with proper response body") {
        mockMvc
          .post("/push/v1/notifyEncrypted/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = payload
          }
          .andExpect { status { isOk() } }
      }

      should("resolve to 400 Bad Request with invalid ciphertext length") {
        mockMvc
          .post("/push/v1/notifyEncrypted/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = invalidPayload
          }
          .andExpect { status { isBadRequest() } }
      }

      should("resolve to 400 Bad Request with duplicate ids") {
        mockMvc
          .post("/push/v1/notifyEncrypted/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = payloadWithDuplicateIds
          }
          .andExpect {
            status { isBadRequest() }
            content {
              contentType(MediaType.APPLICATION_JSON)
              jsonPath("$.error") { value("Duplicate notification IDs in batch") }
              jsonPath("$.details.duplicate_ids") {
                value(containsInAnyOrder("id1", "id2"))
                value(not(hasItem("id3")))
              }
            }
          }
      }
    }
  })
