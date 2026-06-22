package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        // Optional: blank/omitted assigns the ticket to the caller's own membership.
        @Size(max = 36) String memberRid,
        // When true the ticket is a group ticket (no owner, shared by the whole trip) and memberRid
        // is ignored. Creating one needs EDITOR, like assigning to another member.
        Boolean shared,
        @NotBlank @Size(max = 200) String title,
        Category ticketType,
        // Optional: a boarding pass may be seat-only, with no scannable QR string.
        @Size(max = 4000) String qrData,
        @Size(max = 50) String seat,
        // Optional link to the itinerary item this ticket is for (EVENT | TRANSPORT | ACCOMMODATION).
        @Size(max = 20) String itineraryKind,
        @Size(max = 36) String itineraryRid,
        @Size(max = 2000) String note) {
}
