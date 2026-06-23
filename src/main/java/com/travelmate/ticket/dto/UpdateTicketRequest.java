package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update — only non-null fields applied. A non-null {@code memberRids} replaces the ticket's
 * member set; {@code shared}=true converts it to a group ticket (clears members). Changing the
 * members (to a group or to anyone but yourself) needs EDITOR.
 */
public record UpdateTicketRequest(
        List<String> memberRids,
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
