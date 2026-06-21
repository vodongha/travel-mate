package com.travelmate.notification.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Initializes the Firebase Admin SDK so {@link FirebaseFcmSender} can send pushes. Only active when
 * {@code app.fcm.enabled=true}; the credentials come from {@code app.fcm.credentials} (inline
 * service-account JSON) or, if blank, Application Default Credentials.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp(@Value("${app.fcm.credentials:}") String credentials) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        final GoogleCredentials creds = (credentials != null && !credentials.isBlank())
                ? GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentials.getBytes(StandardCharsets.UTF_8)))
                : GoogleCredentials.getApplicationDefault();
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(creds).build());
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
