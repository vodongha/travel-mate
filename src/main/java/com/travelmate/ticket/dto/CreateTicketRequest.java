package com.travelmate.ticket.dto;

import com.travelmate.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        // Optional: blank/omitted assigns the ticket to the caller's own membership.
        @Size(max = 36) String memberRid,
        @NotBlank @Size(max = 200) String title,
        TicketType ticketType,
        @NotBlank @Size(max = 4000) String qrData,
        @Size(max = 50) String seat,
        @Size(max = 2000) String note) {
}
