package com.travelmate.place.dto;

import com.travelmate.place.Place;
import com.travelmate.place.PlaceType;

import java.math.BigDecimal;

public record PlaceResponse(
        String rid,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        PlaceType placeType) {

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
