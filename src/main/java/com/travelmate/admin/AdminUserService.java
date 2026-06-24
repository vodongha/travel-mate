package com.travelmate.admin;

import com.travelmate.user.AuthTokenRepository;
import com.travelmate.user.AuthTokenType;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * User administration for the {@code /admin} panel: list/detail, edit, reset password, grant/revoke
 * the super-admin flag, and disable/restore. Every mutation writes an {@link AdminAuditLog} entry.
 * Safety guards stop an admin locking everyone out (no self-disable, no self-revoke, never remove the
 * last admin). Errors are thrown as {@link AdminActionException} for the controller to flash.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminService adminService;

    public AdminUserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                            PasswordEncoder passwordEncoder, AdminService adminService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminService = adminService;
    }

    @Transactional(readOnly = true)
    public Page<User> list(String query, Pageable pageable) {
        return userRepository.search(query == null ? "" : query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public User get(String rid) {
        return userRepository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("User not found."));
    }

    /** Edit display name and/or email. Email must stay unique among live accounts. */
    @Transactional
    public void updateProfile(User actor, String rid, String name, String email) {
        User user = get(rid);
        String newName = name == null ? "" : name.trim();
        String newEmail = email == null ? "" : email.trim().toLowerCase();
        if (newName.isEmpty()) {
            throw new AdminActionException("Name is required.");
        }
        if (newEmail.isEmpty()) {
            throw new AdminActionException("Email is required.");
        }
        if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new AdminActionException("Another account already uses that email.");
        }
        String before = user.getName() + " <" + user.getEmail() + ">";
        user.setName(newName);
        user.setEmail(newEmail);
        adminService.audit(actor.getId(), "USER_EDIT", "USER", rid,
                before + " -> " + newName + " <" + newEmail + ">");
    }

    /** Set a new password (BCrypt) and revoke the user's refresh tokens so they must sign in again. */
    @Transactional
    public void resetPassword(User actor, String rid, String newPassword) {
        User user = get(rid);
        if (newPassword == null || newPassword.length() < 8) {
            throw new AdminActionException("Password must be at least 8 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        revokeRefreshTokens(user);
        adminService.audit(actor.getId(), "USER_RESET_PASSWORD", "USER", rid, null);
    }

    /** Grant or revoke the platform super-admin flag, with last-admin and self guards. */
    @Transactional
    public void setAdmin(User actor, String rid, boolean grant) {
        User user = get(rid);
        if (!grant && user.getId().equals(actor.getId())) {
            throw new AdminActionException("You cannot revoke your own admin access.");
        }
        if (!grant && user.isSuperadmin() && userRepository.countBySuperadminTrue() <= 1) {
            throw new AdminActionException("Cannot revoke the last remaining admin.");
        }
        if (grant && (user.getPasswordHash() == null || user.getPasswordHash().isBlank())) {
            throw new AdminActionException(
                    "This account has no password (Google-only) and cannot sign in to the admin panel.");
        }
        if (user.isSuperadmin() == grant) {
            return; // no-op, no audit noise
        }
        user.setSuperadmin(grant);
        adminService.audit(actor.getId(), grant ? "USER_GRANT_ADMIN" : "USER_REVOKE_ADMIN",
                "USER", rid, null);
    }

    /** Disable or restore an account. Disabling revokes refresh tokens; you cannot disable yourself. */
    @Transactional
    public void setDisabled(User actor, String rid, boolean disabled) {
        User user = get(rid);
        if (disabled && user.getId().equals(actor.getId())) {
            throw new AdminActionException("You cannot disable your own account.");
        }
        if (user.isDisabled() == disabled) {
            return;
        }
        user.setDisabled(disabled);
        if (disabled) {
            revokeRefreshTokens(user);
        }
        adminService.audit(actor.getId(), disabled ? "USER_DISABLE" : "USER_RESTORE", "USER", rid, null);
    }

    private void revokeRefreshTokens(User user) {
        authTokenRepository.markAllUnusedAsUsed(user.getId(), AuthTokenType.REFRESH, Instant.now());
    }
}
