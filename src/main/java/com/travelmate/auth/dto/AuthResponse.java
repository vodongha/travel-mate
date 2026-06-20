package com.travelmate.auth.dto;

import com.travelmate.user.dto.UserResponse;

/**
 * Result of a successful authentication: a short-lived bearer access token plus the opaque refresh
 * token (rotated on every {@code /auth/refresh}) and the user's public profile.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        UserResponse user) {

    public static AuthResponse of(String accessToken, long expiresIn, String refreshToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
    }
}
