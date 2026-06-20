package com.travelmate.user.dto;

import jakarta.validation.constraints.Size;

/** Partial update of the current user's profile — only non-null fields are applied. */
public record UpdateMeRequest(
        @Size(min = 1, max = 150) String name,
        @Size(max = 500) String avatar,
        @Size(max = 64) String timezone,
        @Size(min = 3, max = 3) String defaultCurrency) {
}
