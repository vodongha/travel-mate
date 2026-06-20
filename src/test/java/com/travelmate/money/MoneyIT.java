package com.travelmate.money;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5 money flow against a real Oracle container: budgets (incl. duplicate 409), expense with an
 * EQUAL split in the base currency, a manual exchange-rate override (amountBase = amount * rate),
 * a fund-paid expense (no shares), and access control (VIEWER cannot create).
 */
class MoneyIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void budgets_createDuplicateAndList() {
        String owner = registerToken("money-owner1@example.com");
        String tripRid = createTrip(owner, "Budget trip");

        JsonNode b = post("/api/v1/trips/" + tripRid + "/budgets",
                Map.of("category", "FOOD", "plannedAmount", "5000000"), owner).getBody().get("data");
        assertThat(b.get("rid").asText()).isNotBlank();
        assertThat(b.has("id")).isFalse();

        // a second FOOD budget conflicts
        assertThat(post("/api/v1/trips/" + tripRid + "/budgets",
                Map.of("category", "FOOD", "plannedAmount", "1"), owner).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        post("/api/v1/trips/" + tripRid + "/budgets",
                Map.of("category", "TRANSPORT", "plannedAmount", "2000000"), owner);
        assertThat(get("/api/v1/trips/" + tripRid + "/budgets", owner).getBody().get("data").size())
                .isEqualTo(2);
    }

    @Test
    void expense_equalSplitInBaseCurrency_sharesSumToAmount() {
        String owner = registerToken("money-owner2@example.com");
        String tripRid = createTrip(owner, "Da Lat 2026");
        String ownerMember = ownerMemberRid(tripRid, owner);
        String lan = addGhost(tripRid, owner, "Lan");
        String minh = addGhost(tripRid, owner, "Minh");

        JsonNode expense = post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Dinner", "category", "FOOD", "currency", "VND", "amount", "90000",
                "payerRid", ownerMember, "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", ownerMember),
                        Map.of("memberRid", lan), Map.of("memberRid", minh))
        ), owner).getBody().get("data");

        assertThat(expense.get("exchangeRate").asDouble()).isEqualTo(1.0);
        assertThat(expense.get("amountBase").asDouble()).isEqualTo(90000.0);
        JsonNode shares = expense.get("shares");
        assertThat(shares.size()).isEqualTo(3);
        double total = 0;
        for (JsonNode s : shares) {
            total += s.get("shareBase").asDouble();
        }
        assertThat(total).isEqualTo(90000.0);
    }

    @Test
    void expense_manualRateOverride_computesAmountBase() {
        String owner = registerToken("money-owner3@example.com");
        String tripRid = createTrip(owner, "Tokyo 2026"); // base VND
        String ownerMember = ownerMemberRid(tripRid, owner);

        JsonNode expense = post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Ramen", "category", "FOOD", "currency", "JPY", "amount", "1500",
                "exchangeRate", "170", "payerRid", ownerMember, "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", ownerMember))
        ), owner).getBody().get("data");

        // 1500 JPY * 170 = 255000 VND base
        assertThat(expense.get("amountBase").asDouble()).isEqualTo(255000.0);
        assertThat(expense.get("shares").get(0).get("shareBase").asDouble()).isEqualTo(255000.0);
    }

    @Test
    void expense_paidFromFund_hasNoShares() {
        String owner = registerToken("money-owner4@example.com");
        String tripRid = createTrip(owner, "Fund trip");
        String ownerMember = ownerMemberRid(tripRid, owner);

        JsonNode expense = post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Group taxi", "category", "TRANSPORT", "currency", "VND", "amount", "200000",
                "payerRid", ownerMember, "paidFromFund", true
        ), owner).getBody().get("data");

        assertThat(expense.get("paidFromFund").asBoolean()).isTrue();
        assertThat(expense.get("shares").size()).isZero();
    }

    @Test
    void viewer_cannotCreateExpense() {
        String owner = registerToken("money-owner5@example.com");
        String tripRid = createTrip(owner, "Read-only money");
        String ownerMember = ownerMemberRid(tripRid, owner);

        String inviteToken = post("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data").get("token").asText();
        String viewer = registerToken("money-viewer5@example.com");
        post("/api/v1/invitations/" + inviteToken + "/accept", Map.of(), viewer);

        ResponseEntity<JsonNode> denied = post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Sneaky", "category", "OTHER", "currency", "VND", "amount", "1",
                "payerRid", ownerMember, "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", ownerMember))), viewer);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
        throw new IllegalStateException("no real member found");
    }

    private String addGhost(String tripRid, String token, String name) {
        return post("/api/v1/trips/" + tripRid + "/members",
                Map.of("displayName", name, "role", "VIEWER"), token).getBody().get("data").get("rid").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
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
