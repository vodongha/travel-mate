package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.ticket.Ticket;

public record TicketResponse(
        String rid,
        String memberRid,
        String memberName,
        boolean mine,
        // True for a group ticket (no owner, shared by the whole trip); memberRid/memberName are null.
        boolean shared,
        String title,
        Category ticketType,
        String qrData,
        String seat,
        String note) {

    public static TicketResponse from(Ticket t, String memberRid, String memberName, boolean mine) {
        return new TicketResponse(t.getRid(), memberRid, memberName, mine, t.getMemberId() == null,
                t.getTitle(), t.getTicketType(), t.getQrData(), t.getSeat(), t.getNote());
    }
}
