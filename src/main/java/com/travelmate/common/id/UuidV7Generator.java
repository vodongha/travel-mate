package com.travelmate.common.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * Generates UUID v7 (time-ordered) values for entity {@code RID}s.
 *
 * <p>Why v7 and not Oracle {@code SYS_GUID()} or {@link UUID#randomUUID()} (v4): v7 embeds a
 * millisecond timestamp in its high bits, so freshly minted ids sort by creation time. That gives
 * far better B-tree index locality than the random v4/SYS_GUID, which scatters inserts across the
 * index. Java 21 has no built-in v7, so we lean on the {@code java-uuid-generator} library.
 */
public final class UuidV7Generator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7Generator() {
    }

    public static String newRid() {
        return GENERATOR.generate().toString();
    }
}
