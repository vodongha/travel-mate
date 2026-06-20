package com.travelmate.user.dto;

import com.travelmate.user.AuthProvider;
import com.travelmate.user.User;

/** Public view of a user — only {@code rid}, never the internal {@code id} or password hash. */
public record UserResponse(
        String rid,
        String email,
        String name,
        String avatar,
        String timezone,
        String defaultCurrency,
        boolean emailVerified,
        AuthProvider provider) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getRid(),
                user.getEmail(),
                user.getName(),
                user.getAvatar(),
                user.getTimezone(),
                user.getDefaultCurrency(),
                user.isEmailVerified(),
                user.getProvider());
    }
}
