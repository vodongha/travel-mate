package com.travelmate.ticket.dto;

import com.travelmate.ticket.TicketType;
import jakarta.validation.constraints.Size;

/** Partial update — only non-null fields applied. {@code memberRid} reassigns the ticket. */
public record UpdateTicketRequest(
        @Size(max = 36) String memberRid,
        @Size(max = 200) String title,
        TicketType ticketType,
        @Size(max = 4000) String qrData,
        @Size(max = 2000) String note) {
}
