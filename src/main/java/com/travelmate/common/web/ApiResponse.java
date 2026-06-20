package com.travelmate.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ProblemDetail;

/**
 * Standard response envelope (SPEC §4): {@code { data, error, meta }}.
 *
 * <p>Success responses carry {@code data} (and optional {@code meta}, e.g. pagination) with a
 * null {@code error}. Error responses carry an {@link ProblemDetail} (RFC 7807) in {@code error}
 * with null {@code data}; see {@link com.travelmate.common.exception.GlobalExceptionHandler}.
 * Null members are omitted from the JSON.
 *
 * @param data    the payload on success
 * @param error   an RFC 7807 problem detail on failure
 * @param meta    optional metadata (pagination, etc.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ProblemDetail error, Object meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(data, null, meta);
    }

    public static ApiResponse<Void> error(ProblemDetail problem) {
        return new ApiResponse<>(null, problem, null);
    }
}
