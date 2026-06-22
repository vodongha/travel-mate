package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a member — only non-null fields are applied. {@code role} works for any member
 * (the owner can't be demoted). {@code displayName} and {@code email} are editable only for a ghost
 * member (a real member's name comes from their account); the email is what an invitation accept
 * matches on to auto-merge the ghost.
 */
public record UpdateMemberRequest(
        @Size(max = 150) String displayName,
        @Email @Size(max = 320) String email,
        MemberRole role) {
}
