package com.travelmate.user;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.user.dto.ChangePasswordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private AuthTokenRepository authTokenRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        UserDeviceRepository userDeviceRepository = mock(UserDeviceRepository.class);
        authTokenRepository = mock(AuthTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, userDeviceRepository, authTokenRepository, passwordEncoder);
    }

    @Test
    void changePassword_correctCurrent_updatesHashAndRevokesSessions() {
        User user = localUser("a@b.com", "old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");

        userService.changePassword(1L, new ChangePasswordRequest("current1", "newpass12"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(authTokenRepository).markAllUnusedAsUsed(eq(1L), eq(AuthTokenType.REFRESH), any());
    }

    @Test
    void changePassword_wrongCurrent_throwsValidationFailed() {
        User user = localUser("a@b.com", "old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        ApiException ex = catch_(() -> userService.changePassword(1L,
                new ChangePasswordRequest("wrong", "newpass12")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePassword_googleOnlyAccount_setsFirstPasswordWithoutCurrent() {
        User user = localUser("g@b.com", null); // no hash → Google-only
        user.setProvider(AuthProvider.GOOGLE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("firstpass1")).thenReturn("first-hash");

        userService.changePassword(2L, new ChangePasswordRequest(null, "firstpass1"));

        assertThat(user.getPasswordHash()).isEqualTo("first-hash");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void deleteMe_softDeletesAndRevokesRefreshTokens() {
        User user = localUser("a@b.com", "hash");
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        userService.deleteMe(3L);

        assertThat(user.isDeleted()).isTrue();
        verify(authTokenRepository).markAllUnusedAsUsed(eq(3L), eq(AuthTokenType.REFRESH), any());
    }

    private static ApiException catch_(Runnable r) {
        try {
            r.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("Expected ApiException");
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
