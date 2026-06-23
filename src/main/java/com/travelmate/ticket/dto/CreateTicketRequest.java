package com.travelmate.ticket.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTicketRequest(
        // The members this ticket covers. Empty/omitted (and not shared) → the caller's own ticket.
        List<String> memberRids,
        // When true the ticket is a group ticket (no specific members, shared by the whole trip);
        // memberRids is ignored. Creating one — or assigning to anyone but yourself — needs EDITOR.
        Boolean shared,
        @NotBlank @Size(max = 200) String title,
        Category ticketType,
        // Optional: a boarding pass may be seat-only, with no scannable QR string.
        @Size(max = 4000) String qrData,
        @Size(max = 50) String seat,
        // Carrier/airline + booking (PNR) code (mainly for TRANSPORT tickets).
        @Size(max = 150) String provider,
        @Size(max = 100) String bookingCode,
        // Optional link to the itinerary item this ticket is for (EVENT | TRANSPORT | ACCOMMODATION).
        @Size(max = 20) String itineraryKind,
        @Size(max = 36) String itineraryRid,
        @Size(max = 2000) String note) {
}
