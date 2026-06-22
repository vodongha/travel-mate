package com.travelmate.transport.dto;

import com.travelmate.transport.TransportType;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Partial update — only non-null fields are applied. Pass a blank string to clear a text field. */
public record UpdateTransportRequest(
        TransportType transportType,
        @Size(max = 150) String provider,
        @Size(max = 100) String bookingCode,
        @Size(max = 300) String departurePlace,
        @Size(max = 300) String arrivalPlace,
        Instant departureTime,
        Instant arrivalTime,
        @Size(max = 2000) String note) {
}
