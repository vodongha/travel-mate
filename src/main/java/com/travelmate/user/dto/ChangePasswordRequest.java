package com.travelmate.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Change (or, for a Google-only account, set the first) password. {@code currentPassword} is
 * optional in the request shape — it is required and verified only when the account already has a
 * password (enforced in {@link com.travelmate.user.UserService#changePassword}).
 */
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}
