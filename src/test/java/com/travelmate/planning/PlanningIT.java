package com.travelmate.planning;

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
 * M4 planning flow against a real Oracle container: places, events (with a place link), transport
 * and accommodation (QR_DATA string round-trip), checklist (with a ghost-member assignee), plus
 * access control (VIEWER read-only, non-member uniform 404) and the no-{@code id}-leak rule.
 */
class PlanningIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void placesAndEvents_crudAndTimeline() {
        String owner = registerToken("plan-owner1@example.com");
        String tripRid = createTrip(owner, "Kyoto 2026");

        // create a place
        JsonNode place = post("/api/v1/trips/" + tripRid + "/places",
                Map.of("name", "Fushimi Inari", "placeType", "SIGHTSEEING",
                        "latitude", 34.9671, "longitude", 135.7727), owner).getBody().get("data");
        assertThat(place.get("rid").asText()).isNotBlank();
        assertThat(place.has("id")).isFalse();
        String placeRid = place.get("rid").asText();

        // create an event linked to the place
        JsonNode event = post("/api/v1/trips/" + tripRid + "/events",
                Map.of("title", "Hike the shrine", "eventType", "SIGHTSEEING",
                        "startTime", "2026-09-10T01:00:00Z", "placeRid", placeRid), owner).getBody().get("data");
        assertThat(event.get("placeRid").asText()).isEqualTo(placeRid);

        // timeline lists it
        JsonNode events = get("/api/v1/trips/" + tripRid + "/events", owner).getBody().get("data");
        assertThat(events.size()).isEqualTo(1);

        // patch then delete
        String eventRid = event.get("rid").asText();
        JsonNode patched = patch("/api/v1/trips/" + tripRid + "/events/" + eventRid,
                Map.of("title", "Sunrise hike"), owner).getBody().get("data");
        assertThat(patched.get("title").asText()).isEqualTo("Sunrise hike");

        assertThat(delete("/api/v1/trips/" + tripRid + "/events/" + eventRid, owner).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/trips/" + tripRid + "/events", owner).getBody().get("data").size())
                .isZero();
    }

    @Test
    void place_prunedWhenLastEventReleasesIt_andDeletePlaceUnlinksEvents() {
        String owner = registerToken("plan-owner5@example.com");
        String tripRid = createTrip(owner, "Hanoi 2026");

        // A place linked to a single event is removed from the Places tab once that event is deleted.
        String solo = post("/api/v1/trips/" + tripRid + "/places",
                Map.of("name", "Old Quarter"), owner).getBody().get("data").get("rid").asText();
        String e0 = post("/api/v1/trips/" + tripRid + "/events", Map.of("title", "Walk",
                "startTime", "2026-05-01T01:00:00Z", "placeRid", solo), owner)
                .getBody().get("data").get("rid").asText();
        delete("/api/v1/trips/" + tripRid + "/events/" + e0, owner);
        assertThat(get("/api/v1/trips/" + tripRid + "/places", owner).getBody().get("data").size())
                .isZero();

        // A place shared by two events is kept while either still uses it.
        String shared = post("/api/v1/trips/" + tripRid + "/places",
                Map.of("name", "Station"), owner).getBody().get("data").get("rid").asText();
        String e1 = post("/api/v1/trips/" + tripRid + "/events", Map.of("title", "Arrive",
                "startTime", "2026-05-02T01:00:00Z", "placeRid", shared), owner)
                .getBody().get("data").get("rid").asText();
        post("/api/v1/trips/" + tripRid + "/events", Map.of("title", "Depart",
                "startTime", "2026-05-05T01:00:00Z", "placeRid", shared), owner);
        delete("/api/v1/trips/" + tripRid + "/events/" + e1, owner);
        assertThat(get("/api/v1/trips/" + tripRid + "/places", owner).getBody().get("data").size())
                .isEqualTo(1); // the other event still uses it

        // Deleting the place itself keeps the remaining event but strips its location.
        assertThat(delete("/api/v1/trips/" + tripRid + "/places/" + shared, owner).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/trips/" + tripRid + "/places", owner).getBody().get("data").size())
                .isZero();
        JsonNode events = get("/api/v1/trips/" + tripRid + "/events", owner).getBody().get("data");
        assertThat(events.size()).isEqualTo(1); // event survived
        assertThat(events.get(0).path("placeRid").isNull()
                || events.get(0).path("placeRid").isMissingNode()).isTrue();
    }

    @Test
    void transportAndAccommodation_qrDataRoundTrips() {
        String owner = registerToken("plan-owner2@example.com");
        String tripRid = createTrip(owner, "Osaka 2026");
        String ticketQr = "TKT|VN123|SEAT12A|2026-09-09T22:30:00Z";

        JsonNode transport = post("/api/v1/trips/" + tripRid + "/transports",
                Map.of("transportType", "FLIGHT", "provider", "VietnamAirlines",
                        "departureTime", "2026-09-09T22:30:00Z", "arrivalTime", "2026-09-10T05:00:00Z",
                        "qrData", ticketQr), owner).getBody().get("data");
        assertThat(transport.get("qrData").asText()).isEqualTo(ticketQr);
        // re-fetch keeps the QR string intact (stored as a string, not an image — SPEC §2.7)
        String transportRid = transport.get("rid").asText();
        assertThat(get("/api/v1/trips/" + tripRid + "/transports/" + transportRid, owner)
                .getBody().get("data").get("qrData").asText()).isEqualTo(ticketQr);

        JsonNode hotel = post("/api/v1/trips/" + tripRid + "/accommodations",
                Map.of("name", "Hotel Namba", "bookingCode", "BK-9981",
                        "checkinTime", "2026-09-10T06:00:00Z", "checkoutTime", "2026-09-12T03:00:00Z",
                        "qrData", "HOTEL|BK-9981"), owner).getBody().get("data");
        assertThat(hotel.get("qrData").asText()).isEqualTo("HOTEL|BK-9981");
        assertThat(hotel.has("id")).isFalse();
    }

    @Test
    void checklist_withGhostAssigneeAndToggle() {
        String owner = registerToken("plan-owner3@example.com");
        String tripRid = createTrip(owner, "Team trip");

        // a ghost member to assign to
        String memberRid = post("/api/v1/trips/" + tripRid + "/members",
                Map.of("displayName", "Lan", "role", "VIEWER"), owner).getBody().get("data").get("rid").asText();

        JsonNode item = post("/api/v1/trips/" + tripRid + "/checklist",
                Map.of("title", "Book airport taxi", "assigneeRid", memberRid), owner).getBody().get("data");
        assertThat(item.get("assigneeRid").asText()).isEqualTo(memberRid);
        assertThat(item.get("completed").asBoolean()).isFalse();

        JsonNode toggled = patch("/api/v1/trips/" + tripRid + "/checklist/" + item.get("rid").asText(),
                Map.of("completed", true), owner).getBody().get("data");
        assertThat(toggled.get("completed").asBoolean()).isTrue();

        assertThat(get("/api/v1/trips/" + tripRid + "/checklist", owner).getBody().get("data").size())
                .isEqualTo(1);
    }

    @Test
    void accessControl_viewerReadOnly_strangerNotFound() {
        String owner = registerToken("plan-owner4@example.com");
        String tripRid = createTrip(owner, "Hue 2026");
        post("/api/v1/trips/" + tripRid + "/places", Map.of("name", "Imperial City"), owner);

        // a VIEWER joins via invitation
        String inviteToken = post("/api/v1/trips/" + tripRid + "/invitations",
                Map.of("role", "VIEWER"), owner).getBody().get("data").get("token").asText();
        String viewer = registerToken("plan-viewer4@example.com");
        post("/api/v1/invitations/" + inviteToken + "/accept", Map.of(), viewer);

        // viewer reads places but cannot create one (EDITOR required) → 403
        assertThat(get("/api/v1/trips/" + tripRid + "/places", viewer).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> denied = post("/api/v1/trips/" + tripRid + "/places",
                Map.of("name", "Sneaky"), viewer);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // a stranger gets a uniform 404 (no existence leak)
        String stranger = registerToken("plan-stranger4@example.com");
        assertThat(get("/api/v1/trips/" + tripRid + "/places", stranger).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerToken(String email) {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "secret123", "name", email), JsonNode.class);
        return r.getBody().get("data").get("accessToken").asText();
    }

    private String createTrip(String token, String name) {
        return post("/api/v1/trips", Map.of("name", name, "baseCurrency", "VND"), token)
                .getBody().get("data").get("rid").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patch(String path, Map<String, ?> body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, jsonAuth(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), JsonNode.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders jsonAuth(String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
