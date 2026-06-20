package com.travelmate.common.exception;

import com.travelmate.common.web.ApiResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates exceptions into the {@code { data, error, meta }} envelope where {@code error} is an
 * RFC 7807 {@link ProblemDetail} (SPEC §4). Spring MVC's own exceptions (validation, malformed
 * body, etc.) are routed through {@link ResponseEntityExceptionHandler} and re-wrapped so every
 * error — ours or the framework's — shares one shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_PREFIX = "https://travelmate.app/problems/";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return wrap(problem(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return wrap(problem(ErrorCode.OPTIMISTIC_LOCK, "The resource was modified by another request. Reload and retry."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        return wrap(problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(Map.of(
                    "field", error.getField(),
                    "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()));
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(problem.getStatus()).body(ApiResponse.error(problem));
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code.title());
        problem.setType(URI.create(TYPE_PREFIX + code.name().toLowerCase().replace('_', '-')));
        problem.setProperty("code", code.name());
        return problem;
    }

    private ResponseEntity<ApiResponse<Void>> wrap(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(ApiResponse.error(problem));
    }
}
