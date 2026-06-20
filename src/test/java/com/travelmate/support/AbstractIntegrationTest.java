package com.travelmate.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for integration tests. Connects to a <b>docker-compose-managed</b> Oracle Free instance
 * (see {@code docker-compose.yml} — run {@code docker compose up -d} and wait for "healthy" first).
 *
 * <p>We use compose rather than Testcontainers because Testcontainers' docker-java cannot reach
 * this host's Docker Desktop over the named pipe (HTTP 400 on {@code /info}); the docker CLI /
 * compose path works. The connection points at {@code localhost:1521/FREEPDB1} as {@code test/test}
 * by default, overridable via {@code IT_DB_URL/IT_DB_USERNAME/IT_DB_PASSWORD} (e.g. for CI).
 *
 * <p>A compose container persists between runs, so this base runs <b>Flyway clean + migrate</b> on
 * context startup ({@link CleanMigrateConfig}) — every run starts from a clean schema. The Spring
 * context is cached across IT classes, so the clean happens once per {@code mvn verify}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AbstractIntegrationTest.CleanMigrateConfig.class)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("IT_DB_URL", "jdbc:oracle:thin:@localhost:1521/FREEPDB1"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("IT_DB_USERNAME", "test"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "test"));
        // Allow the test-only clean below; production keeps Flyway clean disabled.
        registry.add("spring.flyway.clean-disabled", () -> "false");
        // Deterministic JWT secret + effectively-unlimited rate limit so many auth calls from the
        // single test client IP don't trip the limiter across the shared context.
        registry.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("app.ratelimit.capacity", () -> 1_000_000);
        // Tests drive the dispatcher directly; keep the background poller off for determinism.
        registry.add("app.notifications.scheduler-enabled", () -> "false");
    }

    /** Wipe + re-migrate the schema once per test run so a reused compose DB stays deterministic. */
    @TestConfiguration
    static class CleanMigrateConfig {
        @Bean
        FlywayMigrationStrategy cleanMigrateStrategy() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
