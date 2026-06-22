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
        @NotBlank @Size(max = 4000) String qrData,
        @Size(max = 50) String seat,
        @Size(max = 2000) String note) {
}
