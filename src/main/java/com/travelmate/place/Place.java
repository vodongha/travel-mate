package com.travelmate.place;

import com.travelmate.common.entity.BaseEntity;
import com.travelmate.common.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * A place on the map, scoped to a trip (SPEC §7 Module 4). {@code TRIP_ID} is nullable in the
 * schema to leave room for a future shared catalog, but the app always creates trip-scoped places.
 */
@Entity
@Table(name = "PLACES")
@SQLRestriction("IS_DELETED = 0")
public class Place extends BaseEntity {

    @Column(name = "TRIP_ID")
    private Long tripId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "PLACE_TYPE", nullable = false, length = 20)
    private Category placeType = Category.OTHER;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Category getPlaceType() {
        return placeType;
    }

    public void setPlaceType(Category placeType) {
        this.placeType = placeType;
    }
}
