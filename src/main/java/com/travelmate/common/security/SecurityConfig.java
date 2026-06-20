package com.travelmate.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security (SPEC §5). Auth endpoints and liveness are public; everything else needs
 * a valid bearer token. CORS origins are an explicit whitelist (no {@code *} with credentials).
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProblemHandlers problemHandlers;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityProblemHandlers problemHandlers) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.problemHandlers = problemHandlers;
    }

    /**
     * Dedicated chain for the public privacy page. It runs first (lower order) and matches only
     * {@code GET /api/v1/privacy}: no auth, and — crucially — {@code X-Frame-Options} is disabled so
     * the Flutter in-app WebView and a web {@code <iframe>} can frame it. The default chain keeps
     * frame protection for everything else.
     */
    @Bean
    @Order(1)
    SecurityFilterChain privacyFilterChain(HttpSecurity http) throws Exception {
        // Only GET maps for this path (LegalController), so matching the path alone is sufficient.
        http
                .securityMatcher("/api/v1/privacy")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                // Scope the stateless-JWT rules to the API; the web client is public static content
                // served from the same origin (see webFilterChain).
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Change-password is authenticated even though it lives under /auth/* —
                        // this matcher must precede the /auth/** permitAll below.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ping").permitAll()
                        // GET /api/v1/privacy is handled by privacyFilterChain (order 1).
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(problemHandlers.accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Everything outside {@code /api/**} is the bundled Flutter web client (index.html + assets),
     * served same-origin. It is public static content — no auth, no JWT filter. The SPA deep-link
     * fallback lives in {@code SpaWebConfig}.
     */
    @Bean
    @Order(3)
    SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = allowedOrigins.isBlank()
                ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
