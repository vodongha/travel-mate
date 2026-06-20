package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull MemberRole role) {
}
