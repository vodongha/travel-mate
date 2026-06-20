package com.travelmate.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end auth flow against a real Oracle container: register → me → login → refresh rotation →
 * reuse detection, plus device registration. Verifies the V2 schema, security chain and envelope.
 */
class AuthIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullAuthFlow() {
        String email = "flow@example.com";

        // register -> 201 with tokens + user
        ResponseEntity<JsonNode> registered = post("/api/v1/auth/register",
                Map.of("email", email, "password", "secret123", "name", "Flow User"));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = registered.getBody().get("data");
        String accessToken = data.get("accessToken").asText();
        String refreshToken = data.get("refreshToken").asText();
        assertThat(data.get("user").get("rid").asText()).isNotBlank();
        assertThat(data.get("user").has("id")).isFalse(); // never expose internal id

        // GET /users/me with the access token -> 200
        ResponseEntity<JsonNode> me = get("/api/v1/users/me", accessToken);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("data").get("email").asText()).isEqualTo(email);

        // login -> 200
        ResponseEntity<JsonNode> loggedIn = post("/api/v1/auth/login",
                Map.of("email", email, "password", "secret123"));
        assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);

        // refresh rotates the refresh token -> 200
        ResponseEntity<JsonNode> refreshed = post("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);

        // reusing the now-rotated refresh token -> 401 (reuse detection)
        ResponseEntity<JsonNode> reused = post("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reused.getBody().get("error").get("code").asText()).isEqualTo("UNAUTHENTICATED");

        // register a device with the fresh access token -> 201
        String freshAccess = refreshed.getBody().get("data").get("accessToken").asText();
        ResponseEntity<JsonNode> device = postAuthed("/api/v1/users/me/devices",
                Map.of("fcmToken", "fcm-token-123", "platform", "ANDROID"), freshAccess);
        assertThat(device.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(device.getBody().get("data").get("platform").asText()).isEqualTo("ANDROID");
    }

    @Test
    void me_requiresAuthentication() {
        ResponseEntity<JsonNode> me = rest.getForEntity("/api/v1/users/me", JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me.getBody().get("error").get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void register_rejectsInvalidEmail() {
        ResponseEntity<JsonNode> response = post("/api/v1/auth/register",
                Map.of("email", "not-an-email", "password", "secret123", "name", "X"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body) {
        return rest.postForEntity(path, body, JsonNode.class);
    }

    private ResponseEntity<JsonNode> postAuthed(String path, Map<String, ?> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
