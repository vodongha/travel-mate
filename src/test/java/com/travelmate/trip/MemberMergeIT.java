package com.travelmate.trip;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Merging a ghost into a real member: the manual "merge member" action re-points the ghost's money
 * onto the target and removes the ghost; and a ghost carrying an {@code email} auto-merges when that
 * person accepts the invitation.
 */
class MemberMergeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void mergeGhostIntoOwner_repointsExpenseAndRemovesGhost() {
        String owner = registerToken("mm-owner1@example.com");
        String tripRid = createTrip(owner, "Dalat");
        String ownerRid = myMemberRid(tripRid, owner);
        String ghostRid = addGhost(tripRid, owner, "Lan", null);

        // An expense the ghost paid, split equally between owner and ghost.
        String expenseRid = post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Dinner", "category", "FOOD", "currency", "VND", "amount", 200000,
                "payerRid", ghostRid, "paidFromFund", false, "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", ownerRid), Map.of("memberRid", ghostRid))),
                owner).getBody().get("data").get("rid").asText();

        // Merge the ghost into the owner.
        JsonNode merged = post("/api/v1/trips/" + tripRid + "/members/" + ghostRid + "/merge",
                Map.of("targetRid", ownerRid), owner).getBody().get("data");
        assertThat(merged.get("rid").asText()).isEqualTo(ownerRid);

        // Ghost is gone; only the owner remains.
        JsonNode members = get("/api/v1/trips/" + tripRid + "/members", owner).getBody().get("data");
        assertThat(members.size()).isEqualTo(1);
        assertThat(members.get(0).get("rid").asText()).isEqualTo(ownerRid);

        // The expense survived and is now paid by the owner.
        JsonNode expenses = get("/api/v1/trips/" + tripRid + "/expenses", owner).getBody().get("data");
        assertThat(expenses.size()).isEqualTo(1);
        assertThat(expenses.get(0).get("rid").asText()).isEqualTo(expenseRid);
        assertThat(expenses.get(0).get("payerRid").asText()).isEqualTo(ownerRid);
    }

    @Test
    void cannotMergeIntoItself() {
        String owner = registerToken("mm-owner2@example.com");
        String tripRid = createTrip(owner, "Hoi An");
        String ghostRid = addGhost(tripRid, owner, "Kha", null);
        assertThat(post("/api/v1/trips/" + tripRid + "/members/" + ghostRid + "/merge",
                Map.of("targetRid", ghostRid), owner).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ghostWithEmail_autoMergesOnInvitationAccept() {
        String owner = registerToken("mm-owner3@example.com");
        String tripRid = createTrip(owner, "Phu Quoc");
        // A friendly-named ghost that carries the joiner's email.
        addGhost(tripRid, owner, "Chi", "mm-joiner3@example.com");
        assertThat(get("/api/v1/trips/" + tripRid + "/members", owner).getBody().get("data").size())
                .isEqualTo(2);

        String token = post("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data").get("token").asText();
        String joiner = registerToken("mm-joiner3@example.com");
        post("/api/v1/invitations/" + token + "/accept", Map.of(), joiner);

        // The ghost was claimed in place — still two members, and the joiner sees one as "mine".
        JsonNode members = get("/api/v1/trips/" + tripRid + "/members", joiner).getBody().get("data");
        assertThat(members.size()).isEqualTo(2);
        assertThat(members).anyMatch(m -> m.get("mine").asBoolean() && !m.get("ghost").asBoolean());
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

    private String myMemberRid(String tripRid, String token) {
        for (JsonNode m : get("/api/v1/trips/" + tripRid + "/members", token).getBody().get("data")) {
            if (m.get("mine").asBoolean()) {
                return m.get("rid").asText();
            }
        }
        throw new IllegalStateException("no own membership");
    }

    private String addGhost(String tripRid, String token, String name, String email) {
        Map<String, Object> body = email == null
                ? Map.of("displayName", name, "role", "VIEWER")
                : Map.of("displayName", name, "email", email, "role", "VIEWER");
        return post("/api/v1/trips/" + tripRid + "/members", body, token)
                .getBody().get("data").get("rid").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private static HttpHeaders jsonAuth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
