package com.travelmate.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.common.entity.Category;
import com.travelmate.timeline.Event;
import com.travelmate.trip.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates {@link ScheduledNotification} rows when a trip or event changes (SPEC §6/§7 Module 14).
 * Each generator first cancels its own still-pending rows, then re-inserts from current data, so
 * editing a trip/event keeps reminders consistent without duplicates. Times already in the past are
 * skipped. Delivery is the dispatcher's job; this only enqueues.
 */
@Service
public class NotificationService {

    private static final int EVENT_REMINDER_MINUTES = 30;
    private static final LocalTime DAILY_SEND_TIME = LocalTime.of(9, 0); // local time for day-based reminders

    private static final EnumSet<NotificationType> TRIP_TYPES =
            EnumSet.of(NotificationType.PRE_TRIP_30D, NotificationType.PRE_TRIP_7D,
                    NotificationType.PRE_TRIP_1D, NotificationType.DEBT_REMINDER);

    private final ScheduledNotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationService(ScheduledNotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** (Re)build a trip's pre-trip countdown reminders and the post-trip debt reminder. */
    @Transactional
    public void rescheduleTrip(Trip trip) {
        repository.cancelPendingForTrip(trip.getId(), TRIP_TYPES);
        Instant now = Instant.now();
        ZoneId zone = zoneOf(trip.getTimezone());

        if (trip.getStartDate() != null) {
            scheduleCountdown(trip, NotificationType.PRE_TRIP_30D, 30, zone, now);
            scheduleCountdown(trip, NotificationType.PRE_TRIP_7D, 7, zone, now);
            scheduleCountdown(trip, NotificationType.PRE_TRIP_1D, 1, zone, now);
        }
        if (trip.getEndDate() != null) {
            Instant at = trip.getEndDate().plusDays(1).atTime(DAILY_SEND_TIME).atZone(zone).toInstant();
            if (at.isAfter(now)) {
                enqueue(trip.getId(), null, null, NotificationType.DEBT_REMINDER, at,
                        payload("notif.debt.title", List.of(trip.getName()),
                                "notif.debt.body", List.of(),
                                "/trips/" + trip.getRid() + "/settlement"));
            }
        }
    }

    /** (Re)build an event's reminder (and hotel check-in, for HOTEL events). */
    @Transactional
    public void rescheduleEvent(Trip trip, Event event) {
        repository.cancelPendingForEvent(event.getId());
        Instant now = Instant.now();
        if (event.getStartTime() == null) {
            return;
        }
        Instant remindAt = event.getStartTime().minus(Duration.ofMinutes(EVENT_REMINDER_MINUTES));
        if (remindAt.isAfter(now)) {
            enqueue(trip.getId(), null, event.getId(), NotificationType.EVENT_REMINDER, remindAt,
                    payload("notif.event.title", List.of(event.getTitle()),
                            "notif.event.body", List.of(String.valueOf(EVENT_REMINDER_MINUTES)),
                            eventDeeplink(trip, event)));
        }
        // Accommodation check-in reminder. (Accommodation is also a dedicated entity now; this still
        // fires for any ACCOMMODATION-categorised event. TODO: drive check-in off the Accommodation
        // entity's checkinTime — needs a non-event notification target.)
        if (event.getEventType() == Category.ACCOMMODATION && event.getStartTime().isAfter(now)) {
            enqueue(trip.getId(), null, event.getId(), NotificationType.HOTEL_CHECKIN, event.getStartTime(),
                    payload("notif.checkin.title", List.of(event.getTitle()),
                            "notif.checkin.body", List.of(),
                            eventDeeplink(trip, event)));
        }
    }

    /** Cancel an event's pending reminders (on event deletion). */
    @Transactional
    public void cancelEvent(Long eventId) {
        repository.cancelPendingForEvent(eventId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void scheduleCountdown(Trip trip, NotificationType type, int daysBefore, ZoneId zone, Instant now) {
        Instant at = trip.getStartDate().minusDays(daysBefore).atTime(DAILY_SEND_TIME).atZone(zone).toInstant();
        if (at.isAfter(now)) {
            String destination =
                    trip.getDestination() == null ? trip.getName() : trip.getDestination();
            boolean tomorrow = daysBefore == 1;
            String titleKey = tomorrow ? "notif.pretrip.tomorrow.title" : "notif.pretrip.days.title";
            String bodyKey = tomorrow ? "notif.pretrip.tomorrow.body" : "notif.pretrip.days.body";
            List<String> titleArgs = tomorrow
                    ? List.of(trip.getName())
                    : List.of(trip.getName(), String.valueOf(daysBefore));
            List<String> bodyArgs = tomorrow
                    ? List.of(destination)
                    : List.of(destination, String.valueOf(daysBefore));
            enqueue(trip.getId(), null, null, type, at,
                    payload(titleKey, titleArgs, bodyKey, bodyArgs, "/trips/" + trip.getRid()));
        }
    }

    private void enqueue(Long tripId, Long userId, Long eventId, NotificationType type,
                         Instant scheduledAt, String payload) {
        ScheduledNotification n = new ScheduledNotification();
        n.setTripId(tripId);
        n.setUserId(userId);
        n.setEventId(eventId);
        n.setType(type);
        n.setScheduledAt(scheduledAt);
        n.setPayload(payload);
        n.setStatus(NotificationStatus.PENDING);
        repository.save(n);
    }

    /**
     * Stores the notification as a localization spec — a message key + positional args for the title
     * and body, plus the deeplink — so the dispatcher can render it in each recipient device's
     * language at send time (rather than baking in one language here).
     */
    private String payload(String titleKey, List<String> titleArgs,
                           String bodyKey, List<String> bodyArgs, String deeplink) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("titleKey", titleKey);
        map.put("titleArgs", titleArgs);
        map.put("bodyKey", bodyKey);
        map.put("bodyArgs", bodyArgs);
        map.put("deeplink", deeplink);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }

    private static String eventDeeplink(Trip trip, Event event) {
        return "/trips/" + trip.getRid() + "/events/" + event.getRid();
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
