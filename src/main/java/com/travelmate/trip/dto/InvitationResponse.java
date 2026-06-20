package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;

import java.time.Instant;

/**
 * Invitation details. {@code inviteUrl} is the string the client encodes into a QR code (SPEC
 * §2.7 — the server never stores or returns a QR image, only the link string).
 */
public record InvitationResponse(
        String rid,
        String token,
        String inviteUrl,
        MemberRole role,
        Instant expiresAt,
        int maxUses,
        int usedCount) {
}
