package com.travelmate.auth;

import com.travelmate.auth.email.EmailSender;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.security.JwtService;
import com.travelmate.user.AuthProvider;
import com.travelmate.user.AuthToken;
import com.travelmate.user.AuthTokenRepository;
import com.travelmate.user.AuthTokenType;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthTokenRepository authTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authTokenRepository = mock(AuthTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
        EmailSender emailSender = mock(EmailSender.class);
        authService = new AuthService(userRepository, authTokenRepository, passwordEncoder,
                jwtService, googleTokenVerifier, emailSender, 30, 24, 1, "http://localhost:8000");
    }

    @Test
    void login_unknownEmail_isUniformUnauthenticated() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(
                () -> authService.login("Ghost@Example.com", "secret123"), ApiException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void login_wrongPassword_isUniformUnauthenticated() {
        User user = localUser("a@b.com", "hash");
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        ApiException ex = catchThrowableOfType(
                () -> authService.login("a@b.com", "wrong"), ApiException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void login_googleOnlyAccount_cannotUsePassword() {
        User googleUser = localUser("g@b.com", null); // no password hash
        googleUser.setProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("g@b.com")).thenReturn(Optional.of(googleUser));
        ApiException ex = catchThrowableOfType(
                () -> authService.login("g@b.com", "anything1"), ApiException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void register_existingEmail_conflicts() {
        when(userRepository.existsByEmail("dup@b.com")).thenReturn(true);
        ApiException ex = catchThrowableOfType(
                () -> authService.register("Dup@b.com", "secret123", "Dup"), ApiException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void refresh_reusedToken_revokesAllSessionsAndFails() {
        AuthToken spent = new AuthToken();
        spent.setUserId(7L);
        spent.setType(AuthTokenType.REFRESH);
        spent.setExpiresAt(Instant.now().plusSeconds(3600));
        spent.setUsedAt(Instant.now().minusSeconds(10)); // already rotated
        when(authTokenRepository.findByTokenAndType(anyString(), eq(AuthTokenType.REFRESH)))
                .thenReturn(Optional.of(spent));

        ApiException ex = catchThrowableOfType(() -> authService.refresh("raw"), ApiException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(authTokenRepository).markAllUnusedAsUsed(eq(7L), eq(AuthTokenType.REFRESH), any());
    }

    @Test
    void refresh_unknownToken_fails() {
        when(authTokenRepository.findByTokenAndType(anyString(), eq(AuthTokenType.REFRESH)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(ApiException.class);
    }

    private static User localUser(String email, String hash) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setProvider(AuthProvider.LOCAL);
        u.setName("Test");
        return u;
    }
}
