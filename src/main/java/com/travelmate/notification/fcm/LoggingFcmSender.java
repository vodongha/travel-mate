package com.travelmate.notification.fcm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev/default {@link FcmSender}: logs instead of calling Firebase, so the whole notification
 * pipeline is exercisable without credentials. Replace with a real Firebase Admin SDK bean
 * (marked {@code @Primary} or profile-scoped) once a service account is configured.
 */
@Component
public class LoggingFcmSender implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingFcmSender.class);

    @Override
    public void send(String fcmToken, String payloadJson) {
        log.info("[DEV FCM] -> token {}… : {}",
                fcmToken == null ? "null" : fcmToken.substring(0, Math.min(8, fcmToken.length())), payloadJson);
    }
}
