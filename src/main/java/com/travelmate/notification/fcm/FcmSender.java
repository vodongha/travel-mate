package com.travelmate.notification.fcm;

/**
 * Sends a push to one device token. M8 ships a dev {@link LoggingFcmSender}; a real Firebase
 * Admin SDK implementation is a drop-in replacement once a service account is configured
 * (kept out of git). The payload is the stored JSON ({ title, body, deeplink }).
 */
public interface FcmSender {

    void send(String fcmToken, String payloadJson);
}
