package com.travelmate.user;

/** Kind of opaque token stored (hashed) in {@code AUTH_TOKENS}. */
public enum AuthTokenType {
    EMAIL_VERIFY,
    PASSWORD_RESET,
    REFRESH
}
