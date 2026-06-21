package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import com.travelmate.trip.TripMember;

import java.time.Instant;

public record MemberResponse(
        String rid,
        String displayName,
        MemberRole role,
        boolean ghost,
        boolean mine,
        Instant joinedAt) {

    public static MemberResponse from(TripMember member) {
        return from(member, false);
    }

    /** {@code mine} marks the membership belonging to the calling user (for "Myself" UIs). */
    public static MemberResponse from(TripMember member, boolean mine) {
        return new MemberResponse(
                member.getRid(),
                member.getDisplayName(),
                member.getRole(),
                member.isGhost(),
                mine,
                member.getJoinedAt());
    }
}
