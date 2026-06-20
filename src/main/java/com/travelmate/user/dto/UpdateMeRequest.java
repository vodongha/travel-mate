package com.travelmate.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update of the current user's profile — only non-null fields are applied. */
public record UpdateMeRequest(
        @Size(min = 1, max = 150) String name,
        @Size(max = 500) String avatar,
        // E.164-ish: optional leading '+' then 7–15 digits. Blank clears it (handled in the service).
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "phone must be a valid number (optionally +-prefixed)")
        @Size(max = 32) String phone,
        @Size(max = 64) String timezone,
        @Size(min = 3, max = 3) String defaultCurrency) {
}
