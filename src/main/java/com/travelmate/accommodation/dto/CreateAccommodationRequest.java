package com.travelmate.accommodation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAccommodationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        Instant checkinTime,
        Instant checkoutTime,
        @Size(max = 2000) String note) {
}
