package com.travelmate.ticket.dto;

import com.travelmate.ticket.Ticket;
import com.travelmate.ticket.TicketType;

public record TicketResponse(
        String rid,
        String memberRid,
        String memberName,
        boolean mine,
        String title,
        TicketType ticketType,
        String qrData,
        String seat,
        String note) {

    public static TicketResponse from(Ticket t, String memberRid, String memberName, boolean mine) {
        return new TicketResponse(t.getRid(), memberRid, memberName, mine,
                t.getTitle(), t.getTicketType(), t.getQrData(), t.getSeat(), t.getNote());
    }
}
