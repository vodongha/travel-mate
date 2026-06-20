package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import com.travelmate.trip.TripMember;

import java.time.Instant;

public record MemberResponse(
        String rid,
        String displayName,
        MemberRole role,
        boolean ghost,
        Instant joinedAt) {

    public static MemberResponse from(TripMember member) {
        return new MemberResponse(
                member.getRid(),
                member.getDisplayName(),
                member.getRole(),
                member.isGhost(),
                member.getJoinedAt());
    }
}
