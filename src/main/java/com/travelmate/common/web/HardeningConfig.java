package com.travelmate.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the hardening filters (SPEC §5.3) ahead of the security chain: a body-size cap on all
 * requests and a per-IP rate limiter scoped to the auth endpoints.
 */
@Configuration
public class HardeningConfig {

    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
            ProblemWriter problemWriter,
            @Value("${app.request.max-body-bytes:1048576}") long maxBodyBytes) {
        FilterRegistrationBean<RequestSizeLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestSizeLimitFilter(problemWriter, maxBodyBytes));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            ProblemWriter problemWriter,
            @Value("${app.ratelimit.capacity:30}") int capacity,
            @Value("${app.ratelimit.window-seconds:60}") long windowSeconds) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(problemWriter, capacity, windowSeconds));
        registration.addUrlPatterns("/api/v1/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
