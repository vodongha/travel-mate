package com.travelmate.timeline.dto;

import com.travelmate.timeline.EventType;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update — only non-null fields are applied. {@code placeRid} is special: pass a blank
 * string to clear the place link, omit it (null) to leave it unchanged.
 */
public record UpdateEventRequest(
        @Size(max = 200) String title,
        EventType eventType,
        Instant startTime,
        Instant endTime,
        @Size(max = 36) String placeRid,
        @Size(max = 2000) String note) {
}
