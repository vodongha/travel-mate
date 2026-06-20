package com.travelmate.auth.email;

import com.travelmate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev/default {@link EmailSender}: logs the link instead of sending mail, so the verify/reset
 * flows are fully exercisable locally without an email provider. Replace with a real provider
 * implementation (and mark this {@code @ConditionalOnMissingBean} / profile-scoped) before prod.
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendEmailVerification(User user, String verifyLink) {
        log.info("[DEV EMAIL] verify email for {} -> {}", user.getEmail(), verifyLink);
    }

    @Override
    public void sendPasswordReset(User user, String resetLink) {
        log.info("[DEV EMAIL] password reset for {} -> {}", user.getEmail(), resetLink);
    }
}
