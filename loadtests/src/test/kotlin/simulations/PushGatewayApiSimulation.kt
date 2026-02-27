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

package simulations

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.ScenarioBuilder
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import io.gatling.javaapi.http.HttpProtocolBuilder
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PushGatewayApiSimulation : Simulation() {

  /* ============================================================
   * HTTP Protocols
   * ============================================================ */
  val apiProtocol: HttpProtocolBuilder = http
    .baseUrl("http://localhost:8080/push/v1")
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")

  val metricsProtocol: HttpProtocolBuilder = http
    .baseUrl("http://localhost:8081/actuator/metrics")
    .acceptHeader("application/json")

  /* ============================================================
   * Constants
   * ============================================================ */
  val allowedAppIds = listOf(
    "de.gematik.alpha.gatling.apns",
    "de.gematik.beta.gatling.apns",
    "de-gematik-gatling.firebase",
    "de.gematik.gatling.unknown"
  )

  /* ============================================================
   * Helpers
   * ============================================================ */
  fun randomPushkey(): String {
    val base = UUID.randomUUID().toString()
    val r = Random().nextDouble()
    return when {
      r < 0.05 -> "${base}_rejected"
      r < 0.10 -> "${base}_quota"
      else -> base
    }
  }

  fun randomDeviceJson(pushkey: String): String {
    val appId = allowedAppIds.random()
    return """
            {
              "app_id": "$appId",
              "pushkey": "$pushkey",
              "pushkey_ts": 1634025600,
              "data": { "format": "format" },
              "tweaks": { "tweak": "tweak" }
            }
        """.trimIndent()
  }

  fun randomDevicesJson(): String {
    val count = 1 + Random().nextInt(10)
    val pushkeys = generateSequence { randomPushkey() }.distinct().take(count).toList()
    return pushkeys.joinToString(prefix = "[", postfix = "]") { randomDeviceJson(it) }
  }

  fun plainNotificationJson(): String {
    val prio = if (Random().nextBoolean()) "high" else "low"
    return """
            {
              "event_id": "${UUID.randomUUID()}",
              "room_id": "room_${Random().nextInt(1000)}",
              "type": "m.room.message",
              "sender": "user_${Random().nextInt(10000)}",
              "sender_display_name": "Sender ${Random().nextInt(10000)}",
              "room_name": "Room ${Random().nextInt(500)}",
              "user_is_target": ${Random().nextBoolean()},
              "prio": "$prio",
              "content": { "content": "msg-${UUID.randomUUID()}" },
              "counts": { "unread": ${Random().nextInt(5)}, "missed_calls": 0 },
              "devices": ${randomDevicesJson()}
            }
        """.trimIndent()
  }

  fun notifyPayload(): String = plainNotificationJson()

  fun notifyBatchPayload(): String {
    val size = 1 + Random().nextInt(5)
    val items = (1..size).joinToString(",") {
      """
            {
              "id": "${UUID.randomUUID().toString().take(32)}",
              "notification": ${plainNotificationJson()}
            }
            """.trimIndent()
    }
    return """{ "notifications": [ $items ] }"""
  }

  fun encryptedDeviceJson(): String {
    val appId = allowedAppIds.random()
    return """
            {
              "app_id": "$appId",
              "pushkey": "${randomPushkey()}",
              "pushkey_ts": ${System.currentTimeMillis() / 1000}
            }
        """.trimIndent()
  }

  fun encryptedNotificationJson(): String {
    return """
            {
              "ciphertext": "encryptedMessage",
              "time_message_encrypted": "2024-11",
              "key_identifier": "${UUID.randomUUID()}",
              "device": ${encryptedDeviceJson()},
              "prio": "high",
              "counts": { "unread": ${Random().nextInt(5)}, "missed_calls": 0 }
            }
        """.trimIndent()
  }

  fun notifyEncryptedBatchPayload(): String {
    val size = 1 + Random().nextInt(5)
    val items = (1..size).joinToString(",") {
      """
            {
              "id": "${UUID.randomUUID().toString().take(32)}",
              "notification": ${encryptedNotificationJson()}
            }
            """.trimIndent()
    }
    return """{ "notifications": [ $items ] }"""
  }

  /* ============================================================
   * Feeders
   * ============================================================ */
  val notifyFeeder = generateSequence { mapOf("payload" to notifyPayload()) }.iterator()
  val notifyBatchFeeder = generateSequence { mapOf("payload" to notifyBatchPayload()) }.iterator()
  val notifyEncryptedBatchFeeder = generateSequence { mapOf("payload" to notifyEncryptedBatchPayload()) }.iterator()

  /* ============================================================
   * Scenarios
   * ============================================================ */
  val notifyScenario: ScenarioBuilder = scenario("POST /notify")
    .feed { notifyFeeder }
    .exec(
      http("notify")
        .post("/notify")
        .body(StringBody { session -> session.getString("payload") })
        .check(status().`in`(200, 400, 401, 413, 500, 503))
    )

  val notifyBatchScenario: ScenarioBuilder = scenario("POST /notify/batch")
    .feed { notifyBatchFeeder }
    .exec(
      http("notify_batch")
        .post("/notify/batch")
        .body(StringBody { session -> session.getString("payload") })
        .check(status().`in`(200, 400, 401, 413, 500, 503))
    )

  val notifyEncryptedBatchScenario: ScenarioBuilder = scenario("POST /notifyEncrypted/batch")
    .feed { notifyEncryptedBatchFeeder }
    .exec(
      http("notify_encrypted_batch")
        .post("/notifyEncrypted/batch")
        .body(StringBody { session -> session.getString("payload") })
        .check(status().`in`(200, 400, 401, 413, 500, 503))
    )

  /* ============================================================
   * Load Simulation
   * 10.000 requests / 3.600 seconds = 2,7778 rps
   * 2,7778 rps / 3 Endpoints = 0,93 rpes
   * ============================================================ */
  val duration: Duration = 60.minutes
  val durationInSeconds = duration.inWholeSeconds
  init {
    setUp(
      notifyScenario.injectOpen(constantUsersPerSec(0.93).during(durationInSeconds)).protocols(apiProtocol),
      notifyBatchScenario.injectOpen(constantUsersPerSec(0.93).during(durationInSeconds)).protocols(apiProtocol),
      notifyEncryptedBatchScenario.injectOpen(constantUsersPerSec(0.93).during(durationInSeconds)).protocols(apiProtocol),
    ).assertions(global().successfulRequests().percent().gte(95.0))
  }
}
