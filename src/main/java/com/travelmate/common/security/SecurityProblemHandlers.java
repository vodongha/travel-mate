package com.travelmate.common.security;

import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.web.ProblemWriter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Provides Spring Security's 401/403 handlers, rendering them in the shared {@code { data, error,
 * meta }} envelope (these fire inside the filter chain, before the {@code @RestControllerAdvice}
 * can see them).
 */
@Component
public class SecurityProblemHandlers {

    private final ProblemWriter problemWriter;

    public SecurityProblemHandlers(ProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                problemWriter.write(response, ErrorCode.UNAUTHENTICATED,
                        "Authentication is required to access this resource.");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                problemWriter.write(response, ErrorCode.FORBIDDEN,
                        "You do not have permission to access this resource.");
    }
}
