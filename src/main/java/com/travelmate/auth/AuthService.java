package com.travelmate.auth;

import com.travelmate.auth.dto.AuthResponse;
import com.travelmate.auth.email.EmailSender;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.security.JwtService;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.user.AuthProvider;
import com.travelmate.user.AuthToken;
import com.travelmate.user.AuthTokenType;
import com.travelmate.user.AuthTokenRepository;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import com.travelmate.user.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Authentication flows (SPEC §5.1): register, login, Google sign-in, refresh-token rotation with
 * reuse detection, email verification, and password reset.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Passwords are BCrypt-hashed; login failures are uniform (no account enumeration).</li>
 *   <li>Refresh tokens are opaque, stored only as SHA-256 hashes, and rotated on every use; a
 *       reused (already-rotated) token means compromise → revoke all of the user's sessions.</li>
 *   <li>Email-verify / password-reset tokens are single-use with a short TTL.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final TripMemberRepository tripMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailSender emailSender;

    private final Duration refreshTtl;
    private final Duration emailVerifyTtl;
    private final Duration passwordResetTtl;
    private final String publicUrl;

    public AuthService(UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       TripMemberRepository tripMemberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       GoogleTokenVerifier googleTokenVerifier,
                       EmailSender emailSender,
                       @Value("${app.auth.refresh-ttl-days:30}") long refreshTtlDays,
                       @Value("${app.auth.email-verify-ttl-hours:24}") long emailVerifyTtlHours,
                       @Value("${app.auth.password-reset-ttl-hours:1}") long passwordResetTtlHours,
                       @Value("${app.public-url:http://localhost:8000}") String publicUrl) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.emailSender = emailSender;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.emailVerifyTtl = Duration.ofHours(emailVerifyTtlHours);
        this.passwordResetTtl = Duration.ofHours(passwordResetTtlHours);
        this.publicUrl = publicUrl;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword, String name) {
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already registered.");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setName(name.trim());
        user.setProvider(AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(false);
        user = userRepository.save(user);
        claimGhostMemberships(user);

        sendEmailVerification(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        // Uniform failure for unknown email, Google-only account, or wrong password.
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid email or password.");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(idToken);
        if (google.email() == null || google.email().isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Google account has no usable email.");
        }
        String email = normalizeEmail(google.email());
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setName(google.name() != null && !google.name().isBlank() ? google.name() : email);
            created.setProvider(AuthProvider.GOOGLE);
            created.setAvatar(google.picture());
            created.setEmailVerified(google.emailVerified());
            created.setPasswordHash(null);
            return userRepository.save(created);
        });
        claimGhostMemberships(user);
        return buildAuthResponse(user);
    }

    /**
     * Auto-link this account to any ghost member created with the same email: claim the ghost row in
     * place (keep its id so money/ticket references stay valid) so the user instantly joins those
     * trips — no manual accept needed. Skips a trip where they are already a real member.
     */
    private void claimGhostMemberships(User user) {
        final Instant now = Instant.now();
        for (TripMember ghost : tripMemberRepository.findByUserIdIsNullAndEmailIgnoreCase(user.getEmail())) {
            if (tripMemberRepository.existsByTripIdAndUserId(ghost.getTripId(), user.getId())) {
                continue; // already a real member of that trip
            }
            ghost.setUserId(user.getId());
            ghost.setJoinedAt(now);
        }
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        AuthToken token = authTokenRepository
                .findByTokenAndType(OpaqueTokens.hash(rawRefreshToken), AuthTokenType.REFRESH)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid refresh token."));

        if (token.isSpent()) {
            // A rotated token presented again => leaked. Revoke every session for this user.
            authTokenRepository.markAllUnusedAsUsed(token.getUserId(), AuthTokenType.REFRESH, now);
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token reuse detected; all sessions have been revoked.");
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token has expired.");
        }

        token.setUsedAt(now); // rotate: this token can never be used again
        User user = loadUser(token.getUserId());
        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = consumeToken(rawToken, AuthTokenType.EMAIL_VERIFY);
        User user = loadUser(token.getUserId());
        user.setEmailVerified(true);
    }

    /** Always succeeds (no account enumeration); only sends mail if a local account exists. */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.LOCAL) {
                sendPasswordReset(user);
            }
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AuthToken token = consumeToken(rawToken, AuthTokenType.PASSWORD_RESET);
        User user = loadUser(token.getUserId());
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Force re-login everywhere after a password change.
        authTokenRepository.markAllUnusedAsUsed(user.getId(), AuthTokenType.REFRESH, Instant.now());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = issueRefreshToken(user);
        return AuthResponse.of(accessToken, jwtService.accessTtlSeconds(), refreshToken, UserResponse.from(user));
    }

    private String issueRefreshToken(User user) {
        String raw = OpaqueTokens.newRawToken();
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setType(AuthTokenType.REFRESH);
        token.setToken(OpaqueTokens.hash(raw));
        token.setExpiresAt(Instant.now().plus(refreshTtl));
        authTokenRepository.save(token);
        return raw;
    }

    private void sendEmailVerification(User user) {
        String raw = issueOpaqueToken(user, AuthTokenType.EMAIL_VERIFY, emailVerifyTtl);
        emailSender.sendEmailVerification(user, publicUrl + "/verify-email?token=" + raw);
    }

    private void sendPasswordReset(User user) {
        String raw = issueOpaqueToken(user, AuthTokenType.PASSWORD_RESET, passwordResetTtl);
        emailSender.sendPasswordReset(user, publicUrl + "/reset-password?token=" + raw);
    }

    private String issueOpaqueToken(User user, AuthTokenType type, Duration ttl) {
        // Invalidate any outstanding tokens of this type first (one live link at a time).
        authTokenRepository.markAllUnusedAsUsed(user.getId(), type, Instant.now());
        String raw = OpaqueTokens.newRawToken();
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setType(type);
        token.setToken(OpaqueTokens.hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        authTokenRepository.save(token);
        return raw;
    }

    private AuthToken consumeToken(String rawToken, AuthTokenType type) {
        Instant now = Instant.now();
        AuthToken token = authTokenRepository.findByTokenAndType(OpaqueTokens.hash(rawToken), type)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid or expired token."));
        if (token.isSpent() || token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Invalid or expired token.");
        }
        token.setUsedAt(now);
        return token;
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Account no longer exists."));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
