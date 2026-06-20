package com.travelmate.common.web;

import com.travelmate.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, in-memory per-IP rate limiter for the auth endpoints (SPEC §5.3). In-memory is
 * fine on a single instance; a shared store (Redis) would be needed across multiple instances.
 * Registered only for {@code /api/v1/auth/*} via {@code HardeningConfig}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProblemWriter problemWriter;
    private final int capacity;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ProblemWriter problemWriter, int capacity, long windowSeconds) {
        this.problemWriter = problemWriter;
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long window = Instant.now().getEpochSecond() / windowSeconds;
        String key = clientIp(request);

        Window counter = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.window != window) {
                return new Window(window);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > capacity) {
            response.setHeader("Retry-After", Long.toString(windowSeconds));
            problemWriter.write(response, ErrorCode.RATE_LIMITED,
                    "Too many requests. Please retry in a moment.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long window;
        int count;

        Window(long window) {
            this.window = window;
            this.count = 1;
        }
    }
}
