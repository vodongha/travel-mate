package com.travelmate.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmate.support.AbstractIntegrationTest;
import com.travelmate.trip.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notification scheduling + dispatch against a real Oracle container: creating a future trip/event
 * enqueues PENDING rows, the dispatcher delivers due ones (flipping them to SENT), and a second
 * dispatch is a no-op (idempotent).
 */
class NotificationIT extends AbstractIntegrationTest {

    /** Past every scheduled time in this test (trip start is 2099), so all rows are "due". */
    private static final Instant FAR_FUTURE = Instant.parse("2200-01-01T00:00:00Z");

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ScheduledNotificationRepository notificationRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private NotificationDispatcher dispatcher;

    @Test
    void trip_and_event_enqueue_then_dispatch_is_idempotent() {
        String token = registerToken("notif@example.com");
        // a device to receive the push (logged by the dev FcmSender)
        post("/api/v1/users/me/devices", Map.of("fcmToken", "tok-notif-1", "platform", "ANDROID"), token);

        String tripRid = data(post("/api/v1/trips", Map.of(
                "name", "Notif trip", "baseCurrency", "VND", "timezone", "Asia/Ho_Chi_Minh",
                "startDate", "2099-06-01", "endDate", "2099-06-10"), token)).get("rid").asText();
        Long tripId = tripRepository.findByRid(tripRid).orElseThrow().getId();

        // 3 countdowns + 1 debt reminder, all pending
        assertThat(pending(tripId)).hasSize(4);

        // an upcoming event adds an EVENT_REMINDER
        post("/api/v1/trips/" + tripRid + "/events", Map.of(
                "title", "Museum", "eventType", "SIGHTSEEING", "startTime", "2099-06-02T03:00:00Z"), token);
        assertThat(pending(tripId)).hasSize(5);

        // dispatch everything due up to the far future → all delivered, none left pending.
        // dispatchDue is global (not trip-scoped) and the IT context/DB is shared across test
        // classes, so other trips' notifications may also be delivered — assert at-least our 5 here
        // and verify our trip exactly via the trip-scoped checks below.
        int delivered = dispatcher.dispatchDue(FAR_FUTURE);
        assertThat(delivered).isGreaterThanOrEqualTo(5);
        assertThat(pending(tripId)).isEmpty();
        assertThat(byStatus(tripId, NotificationStatus.SENT)).hasSize(5);

        // idempotent: nothing left to send
        assertThat(dispatcher.dispatchDue(FAR_FUTURE)).isZero();
    }

    private List<ScheduledNotification> pending(Long tripId) {
        return byStatus(tripId, NotificationStatus.PENDING);
    }

    private List<ScheduledNotification> byStatus(Long tripId, NotificationStatus status) {
        return notificationRepository.findByTripIdOrderByScheduledAtAsc(tripId).stream()
                .filter(n -> n.getStatus() == status)
                .toList();
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

    private static JsonNode data(ResponseEntity<JsonNode> response) {
        return response.getBody().get("data");
    }
}
