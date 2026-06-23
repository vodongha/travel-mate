package com.travelmate.accommodation.dto;

import com.travelmate.accommodation.Accommodation;

import java.time.Instant;

public record AccommodationResponse(
        String rid,
        String name,
        String address,
        Instant checkinTime,
        Instant checkoutTime,
        String note) {

    public static AccommodationResponse from(Accommodation a) {
        return new AccommodationResponse(
                a.getRid(),
                a.getName(),
                a.getAddress(),
                a.getCheckinTime(),
                a.getCheckoutTime(),
                a.getNote());
    }
}
