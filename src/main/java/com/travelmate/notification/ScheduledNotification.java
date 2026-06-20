package com.travelmate.notification;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A queued notification (SPEC §6 Module 14). Created when a trip/event changes; drained by the
 * dispatch job which sends FCM and flips the status. Recipients: {@code userId} if set, else all
 * members of {@code tripId}. {@code eventId} anchors event-derived reminders for regeneration.
 */
@Entity
@Table(name = "SCHEDULED_NOTIFICATIONS")
@SQLRestriction("IS_DELETED = 0")
public class ScheduledNotification extends BaseEntity {

    @Column(name = "TRIP_ID")
    private Long tripId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "EVENT_ID")
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "PAYLOAD", nullable = false, length = 1000)
    private String payload;

    @Column(name = "SCHEDULED_AT", nullable = false)
    private Instant scheduledAt;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }
}
