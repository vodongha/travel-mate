package com.travelmate.settings;

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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the settings-support slice against a real Oracle container: change-password
 * (happy + wrong current), PATCH/GET {@code /users/me} with phone + display currency, account
 * deletion then token rejection, the public privacy page (vi/en), and that {@code /rates} requires
 * auth. Mirrors {@link com.travelmate.auth.AuthIT}'s {@code TestRestTemplate} style.
 */
class SettingsSupportIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void changePassword_happyPath_thenLoginWithNewPassword() {
        String email = "cp-happy@example.com";
        String access = register(email, "secret123", "CP Happy");

        ResponseEntity<JsonNode> changed = postAuthed("/api/v1/auth/change-password",
                Map.of("currentPassword", "secret123", "newPassword", "brandnew99"), access);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Old password no longer works.
        assertThat(login(email, "secret123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // New password works.
        assertThat(login(email, "brandnew99").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changePassword_wrongCurrent_isBadRequest() {
        String access = register("cp-wrong@example.com", "secret123", "CP Wrong");

        ResponseEntity<JsonNode> changed = postAuthed("/api/v1/auth/change-password",
                Map.of("currentPassword", "not-it99", "newPassword", "brandnew99"), access);

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changed.getBody().get("error").get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void patchMe_updatesPhoneAndCurrency_andGetMeReturnsThem() {
        String access = register("profile@example.com", "secret123", "Profile User");

        Map<String, Object> patch = new HashMap<>();
        patch.put("name", "Renamed");
        patch.put("phone", "+84901234567");
        patch.put("defaultCurrency", "usd"); // lower-case in, upper-case stored

        ResponseEntity<JsonNode> patched = exchange("/api/v1/users/me", HttpMethod.PATCH, patch, access);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = patched.getBody().get("data");
        assertThat(data.get("name").asText()).isEqualTo("Renamed");
        assertThat(data.get("phone").asText()).isEqualTo("+84901234567");
        assertThat(data.get("defaultCurrency").asText()).isEqualTo("USD");
        assertThat(data.get("hasPassword").asBoolean()).isTrue();
        assertThat(data.has("id")).isFalse(); // never expose internal id

        ResponseEntity<JsonNode> me = get("/api/v1/users/me", access);
        assertThat(me.getBody().get("data").get("phone").asText()).isEqualTo("+84901234567");
        assertThat(me.getBody().get("data").get("defaultCurrency").asText()).isEqualTo("USD");
    }

    @Test
    void patchMe_unsupportedCurrency_isBadRequest() {
        String access = register("badcur@example.com", "secret123", "Bad Cur");

        ResponseEntity<JsonNode> patched = exchange("/api/v1/users/me", HttpMethod.PATCH,
                Map.of("defaultCurrency", "ZZZ"), access);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patched.getBody().get("error").get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void deleteMe_softDeletes_andTokenIsRejectedAfterwards() {
        String email = "delete-me@example.com";
        String access = register(email, "secret123", "Delete Me");

        // Token works before deletion.
        assertThat(get("/api/v1/users/me", access).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> deleted = exchange("/api/v1/users/me", HttpMethod.DELETE, null, access);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Same (still cryptographically valid) access token is now rejected.
        assertThat(get("/api/v1/users/me", access).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The email is freed (partial-unique among live rows) — re-register succeeds.
        assertThat(rawRegister(email, "secret123", "Reborn").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void privacy_isPublicHtml_forBothLanguages() {
        ResponseEntity<String> vi = rest.getForEntity("/api/v1/privacy", String.class);
        assertThat(vi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(vi.getHeaders().getContentType().toString()).contains(MediaType.TEXT_HTML_VALUE);
        assertThat(vi.getBody()).contains("Chính sách quyền riêng tư");
        // Framable: no X-Frame-Options so the in-app WebView / iframe can load it.
        assertThat(vi.getHeaders().containsKey("X-Frame-Options")).isFalse();

        ResponseEntity<String> en = rest.getForEntity("/api/v1/privacy?lang=en", String.class);
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(en.getBody()).contains("Privacy Policy");
    }

    @Test
    void rates_requireAuthentication() {
        ResponseEntity<JsonNode> rates = rest.getForEntity("/api/v1/rates", JsonNode.class);
        assertThat(rates.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String register(String email, String password, String name) {
        ResponseEntity<JsonNode> r = rawRegister(email, password, name);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("data").get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> rawRegister(String email, String password, String name) {
        return rest.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", password, "name", name), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), JsonNode.class);
    }

    private ResponseEntity<JsonNode> postAuthed(String path, Map<String, ?> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, Map<String, ?> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
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
