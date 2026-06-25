package com.travelmate.notification;

/** Kinds of scheduled notification (SPEC §6 Module 14). */
public enum NotificationType {
    PRE_TRIP_30D,
    PRE_TRIP_7D,
    PRE_TRIP_1D,
    EVENT_REMINDER,
    HOTEL_CHECKIN,
    DEBT_REMINDER,
    /** An ad-hoc message composed and pushed from the admin panel. */
    ADMIN
}
