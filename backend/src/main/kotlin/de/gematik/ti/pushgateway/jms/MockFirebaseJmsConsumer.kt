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

import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_ENCRYPTED
import de.gematik.ti.pushgateway.jms.FirebaseJmsConsumer.Companion.QUEUE_FIREBASE_PLAIN
import de.gematik.ti.pushgateway.jms.model.EncryptedNotificationMessage
import de.gematik.ti.pushgateway.jms.model.PlainNotificationMessage
import jakarta.jms.Message
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

/** This is intended for load tests only */
@ConditionalOnProperty(name = ["gatling.enabled"], havingValue = "true")
@Component
class MockFirebaseJmsConsumer : JmsConsumer {

  @JmsListener(destination = QUEUE_FIREBASE_PLAIN)
  override fun processPlainNotification(payload: PlainNotificationMessage, message: Message) {
    if (!payload.pushClientInfo.pushKey.contains("quota") || message.jmsRedelivered) {
      message.acknowledge()
    }
  }

  @JmsListener(destination = QUEUE_FIREBASE_ENCRYPTED)
  override fun processEncryptedNotification(
    payload: EncryptedNotificationMessage,
    message: Message,
  ) {
    if (!payload.pushClientInfo.pushKey.contains("quota") || message.jmsRedelivered) {
      message.acknowledge()
    }
  }
}
