package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripStatus;
import com.travelmate.trip.TripType;

import java.time.LocalDate;

/** Public view of a trip plus the caller's role in it. */
public record TripResponse(
        String rid,
        String name,
        String description,
        String destination,
        TripType tripType,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        String baseCurrency,
        TripStatus status,
        MemberRole myRole) {

    public static TripResponse from(Trip trip, MemberRole myRole) {
        return new TripResponse(
                trip.getRid(),
                trip.getName(),
                trip.getDescription(),
                trip.getDestination(),
                trip.getTripType(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getTimezone(),
                trip.getBaseCurrency(),
                trip.getStatus(),
                myRole);
    }
}
