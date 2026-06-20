package com.travelmate.common.security;

import com.travelmate.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-xxxx";

    private final JwtService jwtService = new JwtService(SECRET, java.time.Duration.ofMinutes(60), "travel-mate");

    private static User user() {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", 42L);
        ReflectionTestUtils.setField(u, "rid", "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
        u.setEmail("alice@example.com");
        return u;
    }

    @Test
    void issueThenParse_roundTrips() {
        AuthPrincipal principal = jwtService.parse(jwtService.issueAccessToken(user()));
        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.rid()).isEqualTo("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
        assertThat(principal.email()).isEqualTo("alice@example.com");
    }

    @Test
    void parse_rejectsTamperedToken() {
        String token = jwtService.issueAccessToken(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");
        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void parse_rejectsTokenSignedWithDifferentKey() {
        String foreign = new JwtService("another-secret-also-at-least-32-bytes-long-yyyy",
                java.time.Duration.ofMinutes(60), "travel-mate").issueAccessToken(user());
        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", java.time.Duration.ofMinutes(60), "travel-mate"))
                .isInstanceOf(IllegalStateException.class);
    }
}
