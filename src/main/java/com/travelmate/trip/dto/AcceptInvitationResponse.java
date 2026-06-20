package com.travelmate.trip.dto;

import com.travelmate.trip.MemberRole;

public record AcceptInvitationResponse(String tripRid, MemberRole role) {
}
