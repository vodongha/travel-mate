package com.travelmate.admin;

/**
 * A user-facing failure of an admin-panel action (validation or a safety guard). The controller
 * catches it and shows the message as an error flash, rather than surfacing a stack trace.
 */
public class AdminActionException extends RuntimeException {
    public AdminActionException(String message) {
        super(message);
    }
}
