package com.travelmate.notification.fcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Real {@link FcmSender} (Firebase Admin SDK). Active and {@link Primary} only when
 * {@code app.fcm.enabled=true} — otherwise {@link LoggingFcmSender} is the sole bean. Parses the
 * stored payload JSON ({@code { title, body, deeplink }}) into a notification + data message.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FirebaseFcmSender implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(FirebaseFcmSender.class);

    private final FirebaseMessaging messaging;
    private final ObjectMapper objectMapper;

    public FirebaseFcmSender(FirebaseMessaging messaging, ObjectMapper objectMapper) {
        this.messaging = messaging;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String fcmToken, String payloadJson) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        String title = "";
        String body = "";
        String deeplink = "";
        try {
            final JsonNode node = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
            title = node.path("title").asText("");
            body = node.path("body").asText("");
            deeplink = node.path("deeplink").asText("");
        } catch (Exception e) {
            log.warn("Malformed FCM payload, sending data-only: {}", e.getMessage());
        }

        final Message.Builder builder = Message.builder().setToken(fcmToken);
        if (!title.isBlank() || !body.isBlank()) {
            builder.setNotification(Notification.builder().setTitle(title).setBody(body).build());
        }
        if (!deeplink.isBlank()) {
            builder.putData("deeplink", deeplink);
        }

        try {
            messaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            // A stale token (UNREGISTERED) or transient error: log and move on; the dispatcher
            // treats this delivery as attempted so it isn't retried forever.
            log.warn("FCM send failed for token {}…: {}",
                    fcmToken.substring(0, Math.min(8, fcmToken.length())), e.getMessagingErrorCode());
        }
    }
}
