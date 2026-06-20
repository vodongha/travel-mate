package com.travelmate.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Writes an RFC 7807 problem, wrapped in the {@code { data, error, meta }} envelope, directly to
 * the servlet response. Used by components that fire outside the {@code @RestControllerAdvice}
 * path — servlet filters (rate limit, body size) and Spring Security's 401/403 handlers.
 */
@Component
public class ProblemWriter {

    private static final String TYPE_PREFIX = "https://travelmate.app/problems/";

    private final ObjectMapper objectMapper;

    public ProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProblemDetail problem(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code.title());
        problem.setType(URI.create(TYPE_PREFIX + code.name().toLowerCase().replace('_', '-')));
        problem.setProperty("code", code.name());
        return problem;
    }

    public void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        ProblemDetail problem = problem(code, detail);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(problem));
    }
}
