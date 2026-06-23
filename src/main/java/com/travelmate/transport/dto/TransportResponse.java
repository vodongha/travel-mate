package com.travelmate.transport.dto;

import com.travelmate.transport.Transport;
import com.travelmate.transport.TransportType;

import java.time.Instant;

public record TransportResponse(
        String rid,
        TransportType transportType,
        String departurePlace,
        String arrivalPlace,
        Instant departureTime,
        Instant arrivalTime,
        String note) {

    public static TransportResponse from(Transport t) {
        return new TransportResponse(
                t.getRid(),
                t.getTransportType(),
                t.getDeparturePlace(),
                t.getArrivalPlace(),
                t.getDepartureTime(),
                t.getArrivalTime(),
                t.getNote());
    }
}
