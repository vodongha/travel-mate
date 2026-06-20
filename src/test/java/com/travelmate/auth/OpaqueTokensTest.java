package com.travelmate.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokensTest {

    @Test
    void hash_isDeterministicAndNotTheRawToken() {
        String raw = OpaqueTokens.newRawToken();
        assertThat(OpaqueTokens.hash(raw)).isEqualTo(OpaqueTokens.hash(raw));
        assertThat(OpaqueTokens.hash(raw)).isNotEqualTo(raw);
        assertThat(OpaqueTokens.hash(raw)).hasSize(64); // SHA-256 hex
    }

    @Test
    void newRawToken_isUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(OpaqueTokens.newRawToken())).isTrue();
        }
    }
}
