package com.travelmate.trip;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.trip.dto.AddMemberRequest;
import com.travelmate.trip.dto.MemberResponse;
import com.travelmate.trip.dto.MergeMemberRequest;
import com.travelmate.trip.dto.UpdateMemberRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Trip members incl. ghost members (SPEC §7 Module 3). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/members")
public class TripMemberController {

    private final TripService tripService;

    public TripMemberController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public ApiResponse<List<MemberResponse>> list(@CurrentUser AuthPrincipal principal,
                                                  @PathVariable String tripRid) {
        return ApiResponse.ok(tripService.listMembers(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> addGhost(@CurrentUser AuthPrincipal principal,
                                                @PathVariable String tripRid,
                                                @Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.ok(tripService.addGhostMember(principal.id(), tripRid, request));
    }

    @PatchMapping("/{memberRid}")
    public ApiResponse<MemberResponse> update(@CurrentUser AuthPrincipal principal,
                                              @PathVariable String tripRid,
                                              @PathVariable String memberRid,
                                              @Valid @RequestBody UpdateMemberRequest request) {
        return ApiResponse.ok(tripService.updateMember(principal.id(), tripRid, memberRid, request));
    }

    /**
     * Merge {@code memberRid} (typically a ghost) INTO {@code targetRid}: re-point all of the
     * source's expenses, shares, fund contributions, tickets and checklist assignments to the target,
     * then soft-delete the source. OWNER only.
     */
    @PostMapping("/{memberRid}/merge")
    public ApiResponse<MemberResponse> merge(@CurrentUser AuthPrincipal principal,
                                             @PathVariable String tripRid,
                                             @PathVariable String memberRid,
                                             @Valid @RequestBody MergeMemberRequest request) {
        return ApiResponse.ok(
                tripService.mergeMember(principal.id(), tripRid, memberRid, request.targetRid()));
    }

    @DeleteMapping("/{memberRid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String memberRid) {
        tripService.removeMember(principal.id(), tripRid, memberRid);
    }
}
