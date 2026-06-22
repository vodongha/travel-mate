package com.travelmate.accommodation;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A lodging booking (SPEC §7 Module 7). Times are stored UTC. {@code qrData} holds the decoded
 * booking-QR <em>string</em>, never an image (SPEC §2.7) — the client re-renders the QR from it.
 */
@Entity
@Table(name = "ACCOMMODATIONS")
@SQLRestriction("IS_DELETED = 0")
public class Accommodation extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "BOOKING_CODE", length = 100)
    private String bookingCode;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "CHECKIN_TIME")
    private Instant checkinTime;

    @Column(name = "CHECKOUT_TIME")
    private Instant checkoutTime;

    @Column(name = "NOTE", length = 2000)
    private String note;

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

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Instant getCheckinTime() {
        return checkinTime;
    }

    public void setCheckinTime(Instant checkinTime) {
        this.checkinTime = checkinTime;
    }

    public Instant getCheckoutTime() {
        return checkoutTime;
    }

    public void setCheckoutTime(Instant checkoutTime) {
        this.checkoutTime = checkoutTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
