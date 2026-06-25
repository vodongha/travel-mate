package com.travelmate.ticket;

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
 * Per-member tickets against a real Oracle container: a self-ticket shows up in {@code /mine} with
 * the QR string intact; an EDITOR assigns a ticket to a ghost member; a VIEWER may manage their own
 * ticket but not another member's.
 */
class TicketIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void selfTicket_appearsInMine_qrRoundTrips() {
        String owner = registerToken("tk-owner1@example.com");
        String tripRid = createTrip(owner, "Kyoto");
        String qr = "BUS|SEAT-12A|2026-09-10";

        JsonNode t = post("/api/v1/trips/" + tripRid + "/tickets", Map.of(
                "title", "Bus to Arashiyama", "ticketType", "TRANSPORT", "qrData", qr), owner)
                .getBody().get("data");
        assertThat(t.get("mine").asBoolean()).isTrue();
        assertThat(t.get("qrData").asText()).isEqualTo(qr);
        assertThat(t.has("id")).isFalse();

        JsonNode mine = get("/api/v1/trips/" + tripRid + "/tickets/mine", owner).getBody().get("data");
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("qrData").asText()).isEqualTo(qr);
    }

    @Test
    void editorAssignsToGhost_listedForAll() {
        String owner = registerToken("tk-owner2@example.com");
        String tripRid = createTrip(owner, "Osaka");
        String lan = addGhost(tripRid, owner, "Lan");

        post("/api/v1/trips/" + tripRid + "/tickets",
                Map.of("title", "My pass", "qrData", "OWNER-QR"), owner);
        JsonNode lanTicket = post("/api/v1/trips/" + tripRid + "/tickets", Map.of(
                "memberRids", java.util.List.of(lan), "title", "Lan's pass", "ticketType", "SIGHTSEEING",
                "qrData", "LAN-QR"), owner).getBody().get("data");
        assertThat(lanTicket.get("memberRids").get(0).asText()).isEqualTo(lan);
        assertThat(lanTicket.get("mine").asBoolean()).isFalse();

        assertThat(get("/api/v1/trips/" + tripRid + "/tickets", owner).getBody().get("data").size())
                .isEqualTo(2);
        // the owner's own ticket list only shows theirs
        assertThat(get("/api/v1/trips/" + tripRid + "/tickets/mine", owner).getBody().get("data").size())
                .isEqualTo(1);
    }

    @Test
    void groupTicket_hasNoOwner_visibleToAll() {
        String owner = registerToken("tk-owner4@example.com");
        String tripRid = createTrip(owner, "Danang");

        // shared=true → a group ticket: no owner, not "mine", and it carries the QR.
        JsonNode g = post("/api/v1/trips/" + tripRid + "/tickets", Map.of(
                "shared", true, "title", "Boat charter", "ticketType", "ACCOMMODATION",
                "qrData", "GROUP-QR"), owner).getBody().get("data");
        assertThat(g.get("shared").asBoolean()).isTrue();
        assertThat(g.get("memberRids").size()).isZero();
        assertThat(g.get("mine").asBoolean()).isTrue(); // a group pass is "mine" for everyone
        assertThat(g.get("qrData").asText()).isEqualTo("GROUP-QR");

        // It shows in the trip-wide list, and in everyone's /mine (a shared pass is relevant to all).
        assertThat(get("/api/v1/trips/" + tripRid + "/tickets", owner).getBody().get("data").size())
                .isEqualTo(1);
        JsonNode mine = get("/api/v1/trips/" + tripRid + "/tickets/mine", owner).getBody().get("data");
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("shared").asBoolean()).isTrue();
    }

    @Test
    void ticketCanCoverMultipleMembers() {
        String owner = registerToken("tk-owner6@example.com");
        String tripRid = createTrip(owner, "Hanoi");
        String ownerMember = ownerMemberRid(tripRid, owner);
        String lan = addGhost(tripRid, owner, "Lan");

        JsonNode t = post("/api/v1/trips/" + tripRid + "/tickets", Map.of(
                "memberRids", java.util.List.of(ownerMember, lan), "title", "Family room",
                "ticketType", "ACCOMMODATION", "qrData", "ROOM-QR"), owner).getBody().get("data");
        assertThat(t.get("memberRids").size()).isEqualTo(2);
        assertThat(t.get("shared").asBoolean()).isFalse();
        assertThat(t.get("mine").asBoolean()).isTrue(); // the owner is one of the two
    }

    @Test
    void editKeepingSameMembers_doesNotViolateUniqueConstraint() {
        String owner = registerToken("tk-owner7@example.com");
        String tripRid = createTrip(owner, "Da Nang");
        String ownerMember = ownerMemberRid(tripRid, owner);
        String lan = addGhost(tripRid, owner, "Lan");

        String rid = post("/api/v1/trips/" + tripRid + "/tickets", Map.of(
                "memberRids", java.util.List.of(ownerMember), "title", "My pass",
                "ticketType", "SIGHTSEEING", "qrData", "QR-1"), owner)
                .getBody().get("data").get("rid").asText();

        // Editing while re-sending the same member used to hit ORA-00001 on UK_TICKET_MEMBERS
        // (insert flushed before the delete). It must now succeed.
        ResponseEntity<JsonNode> kept = patch("/api/v1/trips/" + tripRid + "/tickets/" + rid,
                Map.of("title", "My pass v2", "memberRids", java.util.List.of(ownerMember)), owner);
        assertThat(kept.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(kept.getBody().get("data").get("title").asText()).isEqualTo("My pass v2");
        assertThat(kept.getBody().get("data").get("memberRids").size()).isEqualTo(1);

        // Re-assigning to a different set (keeping one, adding one) also round-trips.
        JsonNode changed = patch("/api/v1/trips/" + tripRid + "/tickets/" + rid,
                Map.of("memberRids", java.util.List.of(ownerMember, lan)), owner).getBody().get("data");
        assertThat(changed.get("memberRids").size()).isEqualTo(2);
    }

    @Test
    void viewer_cannotCreateGroupTicket() {
        String owner = registerToken("tk-owner5@example.com");
        String tripRid = createTrip(owner, "Sapa");
        String inviteToken = post("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data").get("token").asText();
        String viewer = registerToken("tk-viewer5@example.com");
        post("/api/v1/invitations/" + inviteToken + "/accept", Map.of(), viewer);

        // A group ticket belongs to no one → creating it is a manage-others action (EDITOR only).
        assertThat(post("/api/v1/trips/" + tripRid + "/tickets",
                Map.of("shared", true, "title", "x", "qrData", "y"), viewer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewer_managesOwnButNotOthers() {
        String owner = registerToken("tk-owner3@example.com");
        String tripRid = createTrip(owner, "Hue");
        String ownerMember = ownerMemberRid(tripRid, owner);

        String inviteToken = post("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data").get("token").asText();
        String viewer = registerToken("tk-viewer3@example.com");
        post("/api/v1/invitations/" + inviteToken + "/accept", Map.of(), viewer);

        // a VIEWER can add their OWN ticket (no memberRid → self)
        assertThat(post("/api/v1/trips/" + tripRid + "/tickets",
                Map.of("title", "My entry", "qrData", "V-QR"), viewer).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        // but not assign one to another member (the owner) → 403
        assertThat(post("/api/v1/trips/" + tripRid + "/tickets",
                Map.of("memberRids", java.util.List.of(ownerMember), "title", "x", "qrData", "y"),
                viewer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerToken(String email) {
        return rest.postForEntity("/api/v1/auth/register",
                        Map.of("email", email, "password", "secret123", "name", email), JsonNode.class)
                .getBody().get("data").get("accessToken").asText();
    }

    private String createTrip(String token, String name) {
        return post("/api/v1/trips", Map.of("name", name, "baseCurrency", "VND"), token)
                .getBody().get("data").get("rid").asText();
    }

    private String ownerMemberRid(String tripRid, String token) {
        for (JsonNode m : get("/api/v1/trips/" + tripRid + "/members", token).getBody().get("data")) {
            if (!m.get("ghost").asBoolean()) {
                return m.get("rid").asText();
            }
        }
        throw new IllegalStateException("no real member");
    }

    private String addGhost(String tripRid, String token, String name) {
        return post("/api/v1/trips/" + tripRid + "/members",
                Map.of("displayName", name, "role", "VIEWER"), token).getBody().get("data").get("rid").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patch(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private static HttpHeaders jsonAuth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
