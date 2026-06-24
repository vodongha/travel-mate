package com.travelmate.admin;

import com.travelmate.user.AuthTokenRepository;
import com.travelmate.user.AuthTokenType;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guards on the admin user actions: no self-revoke/disable, never the last admin, basic validation. */
class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthTokenRepository authTokenRepository = mock(AuthTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminService adminService = mock(AdminService.class);

    private final AdminUserService service =
            new AdminUserService(userRepository, authTokenRepository, passwordEncoder, adminService);

    private static User user(long id, String email, boolean admin) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "rid", "rid-" + id);
        u.setEmail(email);
        u.setName("User " + id);
        u.setPasswordHash("$2a$hash");
        u.setSuperadmin(admin);
        return u;
    }

    @Test
    void cannotRevokeOwnAdmin() {
        User me = user(1, "me@x.com", true);
        when(userRepository.findByRid("rid-1")).thenReturn(Optional.of(me));
        assertThatThrownBy(() -> service.setAdmin(me, "rid-1", false))
                .isInstanceOf(AdminActionException.class)
                .hasMessageContaining("your own");
    }

    @Test
    void cannotRevokeLastAdmin() {
        User me = user(1, "me@x.com", true);
        User other = user(2, "other@x.com", true);
        when(userRepository.findByRid("rid-2")).thenReturn(Optional.of(other));
        when(userRepository.countBySuperadminTrue()).thenReturn(1L);
        assertThatThrownBy(() -> service.setAdmin(me, "rid-2", false))
                .isInstanceOf(AdminActionException.class)
                .hasMessageContaining("last remaining");
    }

    @Test
    void cannotDisableSelf() {
        User me = user(1, "me@x.com", true);
        when(userRepository.findByRid("rid-1")).thenReturn(Optional.of(me));
        assertThatThrownBy(() -> service.setDisabled(me, "rid-1", true))
                .isInstanceOf(AdminActionException.class)
                .hasMessageContaining("your own");
    }

    @Test
    void disablingOtherRevokesTokensAndAudits() {
        User me = user(1, "me@x.com", true);
        User other = user(2, "other@x.com", false);
        when(userRepository.findByRid("rid-2")).thenReturn(Optional.of(other));

        service.setDisabled(me, "rid-2", true);

        assertThat(other.isDisabled()).isTrue();
        verify(authTokenRepository).markAllUnusedAsUsed(eq(2L), eq(AuthTokenType.REFRESH), any());
        verify(adminService).audit(eq(1L), eq("USER_DISABLE"), eq("USER"), eq("rid-2"), any());
    }

    @Test
    void rejectsDuplicateEmailOnEdit() {
        User me = user(1, "me@x.com", true);
        User other = user(2, "other@x.com", false);
        when(userRepository.findByRid("rid-2")).thenReturn(Optional.of(other));
        when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.updateProfile(me, "rid-2", "New Name", "taken@x.com"))
                .isInstanceOf(AdminActionException.class)
                .hasMessageContaining("already uses");
    }

    @Test
    void rejectsShortPassword() {
        User me = user(1, "me@x.com", true);
        User other = user(2, "other@x.com", false);
        when(userRepository.findByRid("rid-2")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.resetPassword(me, "rid-2", "short"))
                .isInstanceOf(AdminActionException.class);
        verify(authTokenRepository, never()).markAllUnusedAsUsed(anyLong(), any(), any());
    }

    @Test
    void resetPasswordHashesAndRevokes() {
        User me = user(1, "me@x.com", true);
        User other = user(2, "other@x.com", false);
        when(userRepository.findByRid("rid-2")).thenReturn(Optional.of(other));
        when(passwordEncoder.encode("longenough1")).thenReturn("$2a$new");

        service.resetPassword(me, "rid-2", "longenough1");

        assertThat(other.getPasswordHash()).isEqualTo("$2a$new");
        verify(authTokenRepository, times(1)).markAllUnusedAsUsed(eq(2L), eq(AuthTokenType.REFRESH), any());
        verify(adminService).audit(eq(1L), eq("USER_RESET_PASSWORD"), eq("USER"), eq("rid-2"), any());
    }
}
