package com.travelmate.transport.dto;

import com.travelmate.transport.TransportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateTransportRequest(
        @NotNull TransportType transportType,
        @Size(max = 150) String provider,
        @Size(max = 100) String bookingCode,
        @Size(max = 300) String departurePlace,
        @Size(max = 300) String arrivalPlace,
        Instant departureTime,
        Instant arrivalTime,
        @Size(max = 4000) String qrData,
        @Size(max = 2000) String note) {
}
