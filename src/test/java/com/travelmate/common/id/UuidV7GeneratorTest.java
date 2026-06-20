package com.travelmate.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the RID generator emits valid, unique, version-7, time-ordered UUIDs. */
class UuidV7GeneratorTest {

    @Test
    void newRid_isVersion7() {
        UUID uuid = UUID.fromString(UuidV7Generator.newRid());
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // IETF variant
    }

    @Test
    void newRid_isUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7Generator.newRid())).isTrue();
        }
    }

    @Test
    void newRid_timestampPrefixIsNonDecreasing() {
        // v7's high 48 bits are a millisecond Unix timestamp. The 74 random low bits mean two ids
        // minted in the same millisecond aren't guaranteed to order against each other, but the
        // timestamp prefix never goes backwards — that's the index-locality property we want.
        long previousTs = timestampMillis(UuidV7Generator.newRid());
        for (int i = 0; i < 1_000; i++) {
            long currentTs = timestampMillis(UuidV7Generator.newRid());
            assertThat(currentTs).isGreaterThanOrEqualTo(previousTs);
            previousTs = currentTs;
        }
    }

    private static long timestampMillis(String rid) {
        return UUID.fromString(rid).getMostSignificantBits() >>> 16;
    }
}
