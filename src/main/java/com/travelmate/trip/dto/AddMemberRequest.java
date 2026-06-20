package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Adds a ghost member (no account) the group can split with. */
public record AddMemberRequest(
        @NotBlank @Size(max = 150) String displayName,
        MemberRole role) {
}
