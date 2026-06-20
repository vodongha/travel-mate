package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Creates an invitation link/QR token. */
public record CreateInvitationRequest(
        MemberRole role,
        @Min(1) @Max(100) Integer maxUses,
        @Min(1) @Max(8760) Integer expiresInHours) {
}
