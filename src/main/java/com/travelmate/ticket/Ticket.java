package com.travelmate.ticket;

import com.travelmate.common.entity.BaseEntity;
import com.travelmate.common.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * A ticket assigned to one trip member (their bus ticket, sightseeing pass, etc.). {@code qrData}
 * is the decoded QR/barcode <em>string</em>, never an image (SPEC §2.7) — the app regenerates the
 * QR. {@code memberId} references TRIP_MEMBERS.ID so a member can show just their own ticket, or is
 * {@code null} for a <em>group</em> ticket — a shared booking (one entry pass for the whole group)
 * that belongs to no single member and that everyone on the trip can see.
 */
@Entity
@Table(name = "TICKETS")
@SQLRestriction("IS_DELETED = 0")
public class Ticket extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    // Null for a group ticket (shared, belongs to the whole trip rather than one member).
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "TICKET_TYPE", nullable = false, length = 20)
    private Category ticketType = Category.OTHER;

    @Column(name = "QR_DATA", nullable = false, length = 4000)
    private String qrData;

    @Column(name = "SEAT", length = 50)
    private String seat;

    @Column(name = "NOTE", length = 2000)
    private String note;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Category getTicketType() {
        return ticketType;
    }

    public void setTicketType(Category ticketType) {
        this.ticketType = ticketType;
    }

    public String getQrData() {
        return qrData;
    }

    public void setQrData(String qrData) {
        this.qrData = qrData;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
