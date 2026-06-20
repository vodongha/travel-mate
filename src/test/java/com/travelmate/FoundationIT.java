package com.travelmate;

import com.travelmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test of the M1 foundation against a real Oracle container: the Spring context
 * loads, Flyway has migrated, and the response envelope is wired through the web layer.
 */
class FoundationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayBaselineApplied() {
        Integer version = jdbc.queryForObject(
                "SELECT MAX(\"installed_rank\") FROM \"flyway_schema_history\"", Integer.class);
        assertThat(version).isNotNull();
    }

    @Test
    void pingReturnsEnvelope() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/ping", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"data\"").contains("\"status\":\"ok\"");
    }
}
