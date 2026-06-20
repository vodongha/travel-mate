package com.travelmate.notification;

/** Lifecycle of a scheduled notification (SPEC §6 Module 14). */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
