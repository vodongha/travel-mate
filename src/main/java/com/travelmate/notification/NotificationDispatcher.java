package com.travelmate.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travelmate.notification.fcm.FcmSender;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.user.UserDevice;
import com.travelmate.user.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drains due {@link ScheduledNotification}s and pushes them via {@link FcmSender}. Idempotent: each
 * row flips PENDING → SENT (or FAILED) once, so a restart never re-sends an already-delivered one.
 * Recipients: the row's {@code userId} if set, otherwise every account-holding member of its trip.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 200;

    private final ScheduledNotificationRepository repository;
    private final TripMemberRepository tripMemberRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmSender fcmSender;
    private final NotificationMessages messages;
    private final ObjectMapper objectMapper;

    public NotificationDispatcher(ScheduledNotificationRepository repository,
                                  TripMemberRepository tripMemberRepository,
                                  UserDeviceRepository userDeviceRepository,
                                  FcmSender fcmSender,
                                  NotificationMessages messages,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.tripMemberRepository = tripMemberRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.fcmSender = fcmSender;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    /** Send everything due at or before {@code now}; returns how many rows were delivered. */
    @Transactional
    public int dispatchDue(Instant now) {
        List<ScheduledNotification> due = repository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        NotificationStatus.PENDING, now, Limit.of(BATCH));
        int delivered = 0;
        for (ScheduledNotification n : due) {
            try {
                for (Long userId : recipients(n)) {
                    for (UserDevice device : userDeviceRepository.findByUserId(userId)) {
                        fcmSender.send(device.getFcmToken(),
                                localize(n.getPayload(), device.getLocale()));
                    }
                }
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(now);
                delivered++;
            } catch (RuntimeException ex) {
                log.warn("Failed to dispatch notification {}: {}", n.getId(), ex.toString());
                n.setStatus(NotificationStatus.FAILED);
            }
        }
        return delivered;
    }

    /**
     * Renders the stored notification into the device's language. New payloads carry a localization
     * spec ({@code titleKey/titleArgs/bodyKey/bodyArgs}); older ones already hold rendered
     * {@code title/body} and pass through unchanged. Always emits {@code { title, body, deeplink }}
     * for the sender. On any parse error, returns the original so delivery still happens.
     */
    private String localize(String payloadJson, String deviceLocale) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
            String deeplink = node.path("deeplink").asText("");
            String title;
            String body;
            if (node.hasNonNull("titleKey")) {
                Locale locale = messages.localeOf(deviceLocale);
                title = messages.render(node.path("titleKey").asText(),
                        stringArgs(node.get("titleArgs")), locale);
                body = messages.render(node.path("bodyKey").asText(),
                        stringArgs(node.get("bodyArgs")), locale);
            } else {
                title = node.path("title").asText("");
                body = node.path("body").asText("");
            }
            ObjectNode out = objectMapper.createObjectNode();
            out.put("title", title);
            out.put("body", body);
            out.put("deeplink", deeplink);
            return objectMapper.writeValueAsString(out);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not localize notification payload, sending as-is: {}", e.toString());
            return payloadJson;
        }
    }

    private static List<String> stringArgs(JsonNode arr) {
        List<String> args = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(a -> args.add(a.asText()));
        }
        return args;
    }

    private List<Long> recipients(ScheduledNotification n) {
        if (n.getUserId() != null) {
            return List.of(n.getUserId());
        }
        if (n.getTripId() != null) {
            return tripMemberRepository.findByTripId(n.getTripId()).stream()
                    .map(TripMember::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }
        return List.of();
    }
}
