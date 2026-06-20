package com.travelmate.timeline.dto;

import com.travelmate.timeline.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String title,
        EventType eventType,
        @NotNull Instant startTime,
        Instant endTime,
        @Size(max = 36) String placeRid,
        @Size(max = 2000) String note) {
}
