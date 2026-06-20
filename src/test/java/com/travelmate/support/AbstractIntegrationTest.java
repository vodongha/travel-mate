package com.travelmate.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests. Boots the full Spring context against a real Oracle Free database in
 * a container (CLAUDE.md "Testing": prefer {@code gvenzl/oracle-free} so tests exercise genuine
 * Oracle behaviour — identity columns, NUMBER(1) booleans, NULL/{@code ''}, function-based indexes
 * — that an in-memory DB would hide). Flyway migrates the container on startup.
 *
 * <p>Requires a running Docker daemon. The container is shared across the test class (static) and
 * reused via Testcontainers' Ryuk-managed lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-free:slim-faststart"))
            .withReuse(true);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }
}
