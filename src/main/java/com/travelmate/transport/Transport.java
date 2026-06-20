package com.travelmate.transport;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A transport leg (SPEC §7 Module 6). Times are stored UTC. {@code qrData} holds the decoded
 * ticket-QR <em>string</em>, never an image (SPEC §2.7) — the client re-renders the QR from it.
 */
@Entity
@Table(name = "TRANSPORTS")
@SQLRestriction("IS_DELETED = 0")
public class Transport extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSPORT_TYPE", nullable = false, length = 20)
    private TransportType transportType;

    @Column(name = "PROVIDER", length = 150)
    private String provider;

    @Column(name = "BOOKING_CODE", length = 100)
    private String bookingCode;

    @Column(name = "DEPARTURE_PLACE", length = 300)
    private String departurePlace;

    @Column(name = "ARRIVAL_PLACE", length = 300)
    private String arrivalPlace;

    @Column(name = "DEPARTURE_TIME")
    private Instant departureTime;

    @Column(name = "ARRIVAL_TIME")
    private Instant arrivalTime;

    @Column(name = "QR_DATA", length = 4000)
    private String qrData;

    @Column(name = "NOTE", length = 2000)
    private String note;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public String getDeparturePlace() {
        return departurePlace;
    }

    public void setDeparturePlace(String departurePlace) {
        this.departurePlace = departurePlace;
    }

    public String getArrivalPlace() {
        return arrivalPlace;
    }

    public void setArrivalPlace(String arrivalPlace) {
        this.arrivalPlace = arrivalPlace;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Instant departureTime) {
        this.departureTime = departureTime;
    }

    public Instant getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getQrData() {
        return qrData;
    }

    public void setQrData(String qrData) {
        this.qrData = qrData;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
