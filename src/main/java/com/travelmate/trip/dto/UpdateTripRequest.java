package com.travelmate.trip.dto;

import com.travelmate.trip.TripStatus;
import com.travelmate.trip.TripType;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Partial trip update — only non-null fields are applied. */
public record UpdateTripRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 300) String destination,
        TripType tripType,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 64) String timezone,
        @Size(min = 3, max = 3) String baseCurrency,
        TripStatus status) {
}
