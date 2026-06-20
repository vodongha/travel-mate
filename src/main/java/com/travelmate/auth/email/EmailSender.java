package com.travelmate.auth.email;

import com.travelmate.user.User;

/**
 * Sends transactional auth emails. M2 ships a dev {@link LoggingEmailSender}; a real provider
 * (Brevo / Resend free tier — Open Decision #4) is a drop-in replacement before production.
 */
public interface EmailSender {

    void sendEmailVerification(User user, String verifyLink);

    void sendPasswordReset(User user, String resetLink);
}
