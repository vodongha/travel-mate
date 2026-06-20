package com.travelmate.trip;

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
 * Trip & member flow against a real Oracle container: create (owner auto-member), access control
 * (non-member 404, viewer 403), and invitation accept with ghost→real merge.
 */
class TripIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createTrip_makesCreatorOwnerMember() {
        String token = registerToken("owner1@example.com");
        JsonNode trip = createTrip(token, "Đà Nẵng 2026").getBody().get("data");
        assertThat(trip.get("rid").asText()).isNotBlank();
        assertThat(trip.get("myRole").asText()).isEqualTo("OWNER");
        assertThat(trip.has("id")).isFalse();

        // listed for the owner
        ResponseEntity<JsonNode> mine = get("/api/v1/trips", token);
        assertThat(mine.getBody().get("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void nonMember_getsUniform404() {
        String owner = registerToken("owner2@example.com");
        String tripRid = createTrip(owner, "Private trip").getBody().get("data").get("rid").asText();

        String stranger = registerToken("stranger@example.com");
        ResponseEntity<JsonNode> resp = get("/api/v1/trips/" + tripRid, stranger);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void invitationAccept_mergesGhostAndGrantsRole() {
        String owner = registerToken("owner3@example.com");
        String tripRid = createTrip(owner, "Team offsite").getBody().get("data").get("rid").asText();
        String inviteeEmail = "invitee3@example.com";

        // owner adds a ghost member named by the invitee's email
        postAuthed("/api/v1/trips/" + tripRid + "/members",
                Map.of("displayName", inviteeEmail, "role", "VIEWER"), owner);

        // owner creates an EDITOR invitation
        JsonNode invite = postAuthed("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "EDITOR", "maxUses", 5), owner).getBody().get("data");
        String inviteToken = invite.get("token").asText();
        assertThat(invite.get("inviteUrl").asText()).contains(inviteToken); // string for client QR

        // invitee registers and accepts → ghost merged in place, role upgraded to EDITOR
        String invitee = registerToken(inviteeEmail);
        ResponseEntity<JsonNode> accept = postAuthed(
                "/api/v1/invitations/" + inviteToken + "/accept", Map.of(), invitee);
        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accept.getBody().get("data").get("role").asText()).isEqualTo("EDITOR");

        // still exactly 2 members (owner + the former ghost, now real), no dangling ghost
        JsonNode members = get("/api/v1/trips/" + tripRid + "/members", owner).getBody().get("data");
        assertThat(members.size()).isEqualTo(2);
        long ghosts = members.findValuesAsText("ghost").stream().filter("true"::equals).count();
        assertThat(ghosts).isZero();

        // invitee now sees the trip as EDITOR
        assertThat(get("/api/v1/trips/" + tripRid, invitee).getBody().get("data").get("myRole").asText())
                .isEqualTo("EDITOR");
    }

    @Test
    void viewer_cannotManageMembers() {
        String owner = registerToken("owner4@example.com");
        String tripRid = createTrip(owner, "Read-only test").getBody().get("data").get("rid").asText();

        JsonNode invite = postAuthed("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data");
        String viewer = registerToken("viewer4@example.com");
        postAuthed("/api/v1/invitations/" + invite.get("token").asText() + "/accept", Map.of(), viewer);

        // A VIEWER may read the trip but not manage members (OWNER-only) → 403.
        assertThat(get("/api/v1/trips/" + tripRid, viewer).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> attempt = postAuthed("/api/v1/trips/" + tripRid + "/members",
                Map.of("displayName", "Sneaky Ghost", "role", "VIEWER"), viewer);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(attempt.getBody().get("error").get("code").asText()).isEqualTo("FORBIDDEN");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerToken(String email) {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "secret123", "name", email), JsonNode.class);
        return r.getBody().get("data").get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> createTrip(String token, String name) {
        return postAuthed("/api/v1/trips", Map.of("name", name, "baseCurrency", "VND"), token);
    }

    private ResponseEntity<JsonNode> postAuthed(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private static HttpHeaders jsonAuth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
