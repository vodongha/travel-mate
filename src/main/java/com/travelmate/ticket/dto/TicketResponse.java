package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.ticket.Ticket;

import java.util.List;

public record TicketResponse(
        String rid,
        // The members this ticket covers (may be several). Empty = a group ticket (whole trip).
        List<String> memberRids,
        List<String> memberNames,
        boolean mine,
        // True for a group ticket (no specific members, shared by the whole trip).
        boolean shared,
        String title,
        Category ticketType,
        String qrData,
        String seat,
        // The itinerary item this ticket is for, if any (kind = EVENT | TRANSPORT | ACCOMMODATION).
        String itineraryKind,
        String itineraryRid,
        String note) {

    public static TicketResponse from(Ticket t, List<String> memberRids, List<String> memberNames,
                                      boolean mine, String itineraryRid) {
        return new TicketResponse(t.getRid(), memberRids, memberNames, mine, memberRids.isEmpty(),
                t.getTitle(), t.getTicketType(), t.getQrData(), t.getSeat(),
                t.getItineraryKind() == null ? null : t.getItineraryKind().name(), itineraryRid,
                t.getNote());
    }
}
