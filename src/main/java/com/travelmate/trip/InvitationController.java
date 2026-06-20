package com.travelmate.trip;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.trip.dto.AcceptInvitationResponse;
import com.travelmate.trip.dto.CreateInvitationRequest;
import com.travelmate.trip.dto.InvitationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trip invitations (SPEC §7 Module 3). Creating requires OWNER (enforced in the service); accepting
 * requires only authentication (the caller becomes a member). The returned {@code inviteUrl} is a
 * string the client renders as a QR code — no image is stored (SPEC §2.7).
 */
@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/trips/{tripRid}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvitationResponse> create(@CurrentUser AuthPrincipal principal,
                                                  @PathVariable String tripRid,
                                                  @Valid @RequestBody CreateInvitationRequest request) {
        return ApiResponse.ok(invitationService.create(principal.id(), tripRid, request));
    }

    @PostMapping("/invitations/{token}/accept")
    public ApiResponse<AcceptInvitationResponse> accept(@CurrentUser AuthPrincipal principal,
                                                        @PathVariable String token) {
        return ApiResponse.ok(invitationService.accept(principal.id(), token));
    }
}
