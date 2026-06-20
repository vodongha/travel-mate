package com.travelmate.accommodation.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Partial update — only non-null fields are applied. Pass a blank string to clear a text field. */
public record UpdateAccommodationRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String bookingCode,
        @Size(max = 500) String address,
        Instant checkinTime,
        Instant checkoutTime,
        @Size(max = 4000) String qrData,
        @Size(max = 2000) String note) {
}
