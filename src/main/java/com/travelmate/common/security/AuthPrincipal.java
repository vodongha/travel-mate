package com.travelmate.common.security;

/**
 * The authenticated user as carried in the {@code SecurityContext}. Holds the internal {@code id}
 * (for service/DB use — never serialized to clients) plus the public {@code rid} and email.
 */
public record AuthPrincipal(Long id, String rid, String email) {
}
