package com.travelmate.place.dto;

import com.travelmate.place.PlaceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Partial update — only non-null fields are applied (PATCH semantics). */
public record UpdatePlaceRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        PlaceType placeType) {
}
