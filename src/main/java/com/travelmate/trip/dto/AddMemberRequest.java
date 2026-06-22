package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adds a ghost member (no account) the group can split with. The optional {@code email} lets the
 * ghost auto-merge into the real account that later joins with that email.
 */
public record AddMemberRequest(
        @NotBlank @Size(max = 150) String displayName,
        @Email @Size(max = 320) String email,
        MemberRole role) {
}
