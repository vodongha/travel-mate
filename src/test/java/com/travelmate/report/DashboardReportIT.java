package com.travelmate.report;

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
 * Dashboard + report aggregation against a real Oracle container. Builds a trip with budgets,
 * personal + fund expenses, a fund contribution and an upcoming event, then asserts the rolled-up
 * numbers (total budget/spent, fund balance, per-category budget-vs-actual, unexpected list).
 */
class DashboardReportIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void dashboardAndReport_aggregateBudgetSpendAndFund() {
        String token = registerToken("m7owner@example.com");
        String tripRid = data(post("/api/v1/trips", Map.of(
                "name", "M7 trip", "baseCurrency", "VND",
                "timezone", "Asia/Ho_Chi_Minh", "startDate", "2099-01-01"), token)).get("rid").asText();
        String memberRid = data(get("/api/v1/trips/" + tripRid + "/members", token)).get(0).get("rid").asText();

        // budgets: FOOD 1,000,000 + TRANSPORT 500,000 = 1,500,000
        post("/api/v1/trips/" + tripRid + "/budgets", Map.of("category", "FOOD", "plannedAmount", 1_000_000), token);
        post("/api/v1/trips/" + tripRid + "/budgets", Map.of("category", "TRANSPORT", "plannedAmount", 500_000), token);

        // personal expenses (VND = base, rate 1): FOOD 300,000 PLANNED + MEDICAL 200,000 UNEXPECTED
        expense(tripRid, token, memberRid, "FOOD", "PLANNED", 300_000);
        expense(tripRid, token, memberRid, "MEDICAL", "UNEXPECTED", 200_000);

        // fund: contribute 1,000,000; fund expense TRANSPORT 400,000
        post("/api/v1/trips/" + tripRid + "/fund/contributions",
                Map.of("memberRid", memberRid, "currency", "VND", "amount", 1_000_000), token);
        post("/api/v1/trips/" + tripRid + "/fund/expenses",
                Map.of("title", "Bus", "category", "TRANSPORT", "currency", "VND", "amount", 400_000), token);

        // an upcoming event
        post("/api/v1/trips/" + tripRid + "/events",
                Map.of("title", "Check-in", "eventType", "HOTEL", "startTime", "2099-01-01T06:00:00Z"), token);

        // ── dashboard ──
        JsonNode dash = data(get("/api/v1/trips/" + tripRid + "/dashboard", token));
        assertThat(dash.get("totalBudget").asDouble()).isEqualTo(1_500_000d);
        assertThat(dash.get("totalSpent").asDouble()).isEqualTo(900_000d);   // 300k + 200k + 400k fund
        assertThat(dash.get("fundBalance").asDouble()).isEqualTo(600_000d);  // 1,000k - 400k
        assertThat(dash.get("countdownDays").asLong()).isGreaterThan(0);
        assertThat(dash.get("nextEvent").get("title").asText()).isEqualTo("Check-in");

        // ── report ──
        JsonNode report = data(get("/api/v1/trips/" + tripRid + "/report", token));
        assertThat(report.get("summary").get("totalBudget").asDouble()).isEqualTo(1_500_000d);
        assertThat(report.get("summary").get("totalActual").asDouble()).isEqualTo(900_000d);
        assertThat(report.get("summary").get("overUnder").asDouble()).isEqualTo(-600_000d);

        Map<String, Double> actualByCat = new java.util.HashMap<>();
        for (JsonNode line : report.get("byCategory")) {
            actualByCat.put(line.get("category").asText(), line.get("actual").asDouble());
        }
        assertThat(actualByCat).containsEntry("FOOD", 300_000d)
                .containsEntry("TRANSPORT", 400_000d)
                .containsEntry("MEDICAL", 200_000d);

        assertThat(report.get("unexpected")).hasSize(1);
        assertThat(report.get("unexpected").get(0).get("category").asText()).isEqualTo("MEDICAL");
        assertThat(report.get("debts")).isEmpty(); // single member → nobody owes anyone
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void expense(String tripRid, String token, String memberRid, String category, String type, int amount) {
        post("/api/v1/trips/" + tripRid + "/expenses", Map.of(
                "title", category + " spend",
                "category", category,
                "expenseType", type,
                "currency", "VND",
                "amount", amount,
                "payerRid", memberRid,
                "paidFromFund", false,
                "splitType", "EQUAL",
                "participants", List.of(Map.of("memberRid", memberRid))), token);
    }

    private String registerToken(String email) {
        return rest.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "secret123", "name", email), JsonNode.class)
                .getBody().get("data").get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private static JsonNode data(ResponseEntity<JsonNode> response) {
        return response.getBody().get("data");
    }
}
