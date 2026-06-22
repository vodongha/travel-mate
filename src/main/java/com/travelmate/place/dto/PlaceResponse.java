package com.travelmate.place.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.place.Place;

import java.math.BigDecimal;

public record PlaceResponse(
        String rid,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Category placeType) {

    public static PlaceResponse from(Place place) {
        return new PlaceResponse(
                place.getRid(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getPlaceType());
    }
}
