package com.travelmate.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.timeline.Event;
import com.travelmate.common.entity.Category;
import com.travelmate.trip.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private ScheduledNotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduledNotificationRepository.class);
        service = new NotificationService(repository, new ObjectMapper());
    }

    private static Trip trip(LocalDate start, LocalDate end) {
        Trip t = new Trip();
        ReflectionTestUtils.setField(t, "id", 1L);
        ReflectionTestUtils.setField(t, "rid", "trip-rid");
        t.setName("Trip");
        t.setTimezone("Asia/Ho_Chi_Minh");
        t.setStartDate(start);
        t.setEndDate(end);
        return t;
    }

    private List<ScheduledNotification> captureSaved() {
        ArgumentCaptor<ScheduledNotification> captor = ArgumentCaptor.forClass(ScheduledNotification.class);
        verify(repository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void rescheduleTrip_futureTrip_enqueuesThreeCountdownsAndDebt() {
        LocalDate start = LocalDate.now().plusDays(60);
        service.rescheduleTrip(trip(start, start.plusDays(5)));

        verify(repository).cancelPendingForTrip(anyLong(), any());
        List<NotificationType> types = captureSaved().stream().map(ScheduledNotification::getType).toList();
        assertThat(types).containsExactlyInAnyOrder(
                NotificationType.PRE_TRIP_30D, NotificationType.PRE_TRIP_7D,
                NotificationType.PRE_TRIP_1D, NotificationType.DEBT_REMINDER);
    }

    @Test
    void rescheduleTrip_pastTrip_enqueuesNothing() {
        LocalDate start = LocalDate.now().minusDays(10);
        service.rescheduleTrip(trip(start, start.plusDays(2)));

        verify(repository).cancelPendingForTrip(anyLong(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rescheduleTrip_onlyOneDayOut_skipsThe30And7Countdowns() {
        LocalDate start = LocalDate.now().plusDays(1);
        service.rescheduleTrip(trip(start, null));

        List<NotificationType> types = captureSaved().stream().map(ScheduledNotification::getType).toList();
        // 30d/7d are in the past and skipped; 1d may or may not be future depending on the send hour,
        // so assert only that the past ones never appear.
        assertThat(types).doesNotContain(NotificationType.PRE_TRIP_30D, NotificationType.PRE_TRIP_7D);
    }

    @Test
    void rescheduleEvent_hotel_enqueuesReminderAndCheckin() {
        Event event = event(Category.ACCOMMODATION, Instant.now().plus(2, ChronoUnit.DAYS));
        service.rescheduleEvent(trip(LocalDate.now().plusDays(2), null), event);

        verify(repository).cancelPendingForEvent(99L);
        List<NotificationType> types = captureSaved().stream().map(ScheduledNotification::getType).toList();
        assertThat(types).containsExactlyInAnyOrder(
                NotificationType.EVENT_REMINDER, NotificationType.HOTEL_CHECKIN);
    }

    @Test
    void rescheduleEvent_nonHotelFuture_enqueuesReminderOnly() {
        Event event = event(Category.FOOD, Instant.now().plus(2, ChronoUnit.DAYS));
        service.rescheduleEvent(trip(LocalDate.now().plusDays(2), null), event);

        List<NotificationType> types = captureSaved().stream().map(ScheduledNotification::getType).toList();
        assertThat(types).containsExactly(NotificationType.EVENT_REMINDER);
    }

    @Test
    void rescheduleEvent_pastEvent_enqueuesNothing() {
        Event event = event(Category.FOOD, Instant.now().minus(2, ChronoUnit.DAYS));
        service.rescheduleEvent(trip(LocalDate.now(), null), event);

        verify(repository).cancelPendingForEvent(anyLong());
        verify(repository, never()).save(any());
    }

    private static Event event(Category type, Instant start) {
        Event e = new Event();
        ReflectionTestUtils.setField(e, "id", 99L);
        ReflectionTestUtils.setField(e, "rid", "event-rid");
        e.setTripId(1L);
        e.setTitle("Ev");
        e.setEventType(type);
        e.setStartTime(start);
        return e;
    }
}
