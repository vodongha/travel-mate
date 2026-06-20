package com.travelmate.common.web;

import com.travelmate.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests whose declared {@code Content-Length} exceeds the configured cap (SPEC §5.3),
 * before the body is read into memory. A chunked request without Content-Length (-1) is passed
 * through to the container's own limits.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final ProblemWriter problemWriter;
    private final long maxBodyBytes;

    public RequestSizeLimitFilter(ProblemWriter problemWriter, long maxBodyBytes) {
        this.problemWriter = problemWriter;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > maxBodyBytes) {
            problemWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request body exceeds the " + maxBodyBytes + "-byte limit.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
