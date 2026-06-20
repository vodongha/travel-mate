package com.travelmate.settlement;

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
 * M6 against a real Oracle container: the derived fund balance (contributions - fund spend -
 * personal-paid-from-fund) and the settlement engine (net balances + minimised transfers), with
 * the fund kept separate from personal settlement.
 */
class FundSettlementIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fundBalance_isDerivedFromContributionsAndSpending() {
        String owner = registerToken("fund-owner1@example.com");
        String tripRid = createTrip(owner, "Fund maths");
        String ownerMember = ownerMemberRid(tripRid, owner);
        String lan = addGhost(tripRid, owner, "Lan");

        contribute(tripRid, owner, ownerMember, "100000");
        contribute(tripRid, owner, lan, "50000");
        post("/api/v1/trips/" + tripRid + "/fund/expenses", Map.of(
                "title", "Snacks", "category", "FOOD", "currency", "VND", "amount", "40000"), owner);
        // a personal expense paid from the fund also draws the balance down
        post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Taxi", "category", "TRANSPORT", "currency", "VND", "amount", "20000",
                "payerRid", ownerMember, "paidFromFund", true), owner);

        JsonNode bal = get("/api/v1/trips/" + tripRid + "/fund/balance", owner).getBody().get("data");
        assertThat(bal.get("totalContributions").asDouble()).isEqualTo(150000.0);
        assertThat(bal.get("totalFundExpenses").asDouble()).isEqualTo(40000.0);
        assertThat(bal.get("totalPersonalPaidFromFund").asDouble()).isEqualTo(20000.0);
        assertThat(bal.get("balance").asDouble()).isEqualTo(90000.0); // 150000 - 40000 - 20000
    }

    @Test
    void settlement_netBalancesAndMinimalTransfers() {
        String owner = registerToken("fund-owner2@example.com");
        String tripRid = createTrip(owner, "Settle up");
        String ownerMember = ownerMemberRid(tripRid, owner);
        String lan = addGhost(tripRid, owner, "Lan");
        String minh = addGhost(tripRid, owner, "Minh");

        // owner pays 90,000 split equally three ways → owner is owed 60,000; lan & minh owe 30,000 each
        post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Dinner", "category", "FOOD", "currency", "VND", "amount", "90000",
                "payerRid", ownerMember, "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", ownerMember),
                        Map.of("memberRid", lan), Map.of("memberRid", minh))), owner);

        JsonNode s = get("/api/v1/trips/" + tripRid + "/settlement", owner).getBody().get("data");

        // owner net +60000
        for (JsonNode b : s.get("balances")) {
            if (b.get("memberRid").asText().equals(ownerMember)) {
                assertThat(b.get("net").asDouble()).isEqualTo(60000.0);
                assertThat(b.get("paid").asDouble()).isEqualTo(90000.0);
            } else {
                assertThat(b.get("net").asDouble()).isEqualTo(-30000.0);
            }
        }
        // two transfers, both to the owner, totalling 60000
        JsonNode tx = s.get("transactions");
        assertThat(tx.size()).isEqualTo(2);
        double received = 0;
        for (JsonNode t : tx) {
            assertThat(t.get("toMemberRid").asText()).isEqualTo(ownerMember);
            received += t.get("amount").asDouble();
        }
        assertThat(received).isEqualTo(60000.0);
    }

    @Test
    void fundPaidExpense_isExcludedFromSettlement() {
        String owner = registerToken("fund-owner3@example.com");
        String tripRid = createTrip(owner, "Fund vs personal");
        String ownerMember = ownerMemberRid(tripRid, owner);

        // a fund-paid expense must NOT create any personal debt
        post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", "Group boat", "category", "ACTIVITY", "currency", "VND", "amount", "500000",
                "payerRid", ownerMember, "paidFromFund", true), owner);

        JsonNode s = get("/api/v1/trips/" + tripRid + "/settlement", owner).getBody().get("data");
        assertThat(s.get("transactions").size()).isZero();
        assertThat(s.get("balances").size()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void contribute(String tripRid, String token, String memberRid, String amount) {
        post("/api/v1/trips/" + tripRid + "/fund/contributions",
                Map.of("memberRid", memberRid, "currency", "VND", "amount", amount), token);
    }

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
