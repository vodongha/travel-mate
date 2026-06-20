package com.travelmate.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application error codes mapped to HTTP statuses (SPEC §4). The {@code code} becomes the
 * machine-readable {@code type}/title hint in the RFC 7807 problem detail; {@code status} is the
 * HTTP status returned.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Invalid or expired token"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Insufficient permission"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT, "Resource was modified concurrently"),
    IDEMPOTENCY_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused with a different payload"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
