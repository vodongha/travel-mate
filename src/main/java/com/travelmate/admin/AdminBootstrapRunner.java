package com.travelmate.admin;

import com.travelmate.user.AuthProvider;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps (or promotes) a platform super-admin on startup — the only way an admin is minted; the
 * {@code /admin} panel never creates one. Runs only when {@code admin.bootstrap.email} is set, so it
 * is a deliberate one-off, not part of normal boot.
 *
 * <p>On Fly.io: {@code fly secrets set ADMIN_BOOTSTRAP_EMAIL=you@example.com ADMIN_PASSWORD=…}
 * (optionally {@code ADMIN_BOOTSTRAP_NAME}), let it restart once, then unset the secrets. An existing
 * account is promoted (password reset only if {@code ADMIN_PASSWORD} is given); otherwise a new
 * email/password admin is created. The password is read from the {@code ADMIN_PASSWORD} env var only,
 * never a CLI argument, so it stays out of process listings and history.
 */
@Component
@ConditionalOnProperty(name = "admin.bootstrap.email")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final int MIN_PASSWORD = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String name;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                @Value("${admin.bootstrap.email}") String email,
                                @Value("${admin.bootstrap.name:}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String normalized = email.trim().toLowerCase();
        final String password = System.getenv("ADMIN_PASSWORD");

        userRepository.findByEmail(normalized).ifPresentOrElse(
                existing -> promote(existing, password),
                () -> create(normalized, password));
    }

    private void promote(User user, String password) {
        user.setSuperadmin(true);
        if (password != null && !password.isBlank()) {
            requireStrong(password);
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        userRepository.save(user);
        log.info("Admin bootstrap: promoted existing account {} to super-admin.", user.getEmail());
    }

    private void create(String normalized, String password) {
        if (password == null || password.isBlank()) {
            log.error("Admin bootstrap: {} has no account and ADMIN_PASSWORD is unset — skipping.",
                    normalized);
            return;
        }
        requireStrong(password);
        User user = new User();
        user.setEmail(normalized);
        user.setName(name == null || name.isBlank() ? normalized.split("@")[0] : name.trim());
        user.setProvider(AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        user.setSuperadmin(true);
        userRepository.save(user);
        log.info("Admin bootstrap: created super-admin account {}.", normalized);
    }

    private static void requireStrong(String password) {
        if (password.length() < MIN_PASSWORD) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD + " characters.");
        }
    }
}
