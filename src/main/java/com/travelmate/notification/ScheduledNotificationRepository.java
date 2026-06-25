package com.travelmate.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, Long> {

    Optional<ScheduledNotification> findByRid(String rid);

    /** Admin search over the message text (payload holds the title/body); empty q matches all. */
    @Query("select n from ScheduledNotification n "
            + "where :q = '' or lower(n.payload) like lower(concat('%', :q, '%'))")
    org.springframework.data.domain.Page<ScheduledNotification> search(
            @Param("q") String q, org.springframework.data.domain.Pageable pageable);

    /** Due, undelivered notifications for the dispatch job (oldest first, capped). */
    List<ScheduledNotification> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status, Instant cutoff, Limit limit);

    List<ScheduledNotification> findByTripIdOrderByScheduledAtAsc(Long tripId);

    /** Cancel a trip's pending trip-level notifications of the given types (for regen on trip edit). */
    @Modifying
    @Query("UPDATE ScheduledNotification n SET n.status = com.travelmate.notification.NotificationStatus.CANCELLED "
            + "WHERE n.tripId = :tripId AND n.eventId IS NULL AND n.deleted = false "
            + "AND n.status = com.travelmate.notification.NotificationStatus.PENDING AND n.type IN :types")
    int cancelPendingForTrip(@Param("tripId") Long tripId, @Param("types") Collection<NotificationType> types);

    /** Cancel an event's pending reminders (for regen on event edit, or on delete). */
    @Modifying
    @Query("UPDATE ScheduledNotification n SET n.status = com.travelmate.notification.NotificationStatus.CANCELLED "
            + "WHERE n.eventId = :eventId AND n.deleted = false "
            + "AND n.status = com.travelmate.notification.NotificationStatus.PENDING")
    int cancelPendingForEvent(@Param("eventId") Long eventId);
}
