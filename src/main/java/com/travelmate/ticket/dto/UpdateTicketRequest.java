package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields applied. {@code memberRid} reassigns the ticket;
 * {@code shared}=true converts it to a group ticket (no owner), {@code shared}=false with a
 * {@code memberRid} reassigns it back to a member. Both need EDITOR.
 */
public record UpdateTicketRequest(
        @Size(max = 36) String memberRid,
        Boolean shared,
        @Size(max = 200) String title,
        Category ticketType,
        @Size(max = 4000) String qrData,
        @Size(max = 50) String seat,
        // Non-null itineraryRid (re)sets the link; blank clears it; omitted leaves it unchanged.
        @Size(max = 20) String itineraryKind,
        @Size(max = 36) String itineraryRid,
        @Size(max = 2000) String note) {
}
