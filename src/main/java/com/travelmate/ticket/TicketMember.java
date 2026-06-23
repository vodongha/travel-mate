package com.travelmate.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Join row: a ticket belongs to a trip member. A ticket may have several (a shared booking covering
 * multiple people); a ticket with none is a group ticket (the whole trip). Pure join — no rid/audit;
 * rows are hard-deleted/replaced when a ticket's members change.
 */
@Entity
@Table(name = "TICKET_MEMBERS")
public class TicketMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TICKET_ID", nullable = false)
    private Long ticketId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    public TicketMember() {
    }

    public TicketMember(Long ticketId, Long memberId) {
        this.ticketId = ticketId;
        this.memberId = memberId;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }
}
