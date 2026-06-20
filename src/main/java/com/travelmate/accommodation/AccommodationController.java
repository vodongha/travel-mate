package com.travelmate.accommodation;

import com.travelmate.accommodation.dto.AccommodationResponse;
import com.travelmate.accommodation.dto.CreateAccommodationRequest;
import com.travelmate.accommodation.dto.UpdateAccommodationRequest;
import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
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

/** Trip accommodations (SPEC §7 Module 7). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/accommodations")
public class AccommodationController {

    private final AccommodationService accommodationService;

    public AccommodationController(AccommodationService accommodationService) {
        this.accommodationService = accommodationService;
    }

    @GetMapping
    public ApiResponse<List<AccommodationResponse>> list(@CurrentUser AuthPrincipal principal,
                                                         @PathVariable String tripRid) {
        return ApiResponse.ok(accommodationService.list(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccommodationResponse> create(@CurrentUser AuthPrincipal principal,
                                                     @PathVariable String tripRid,
                                                     @Valid @RequestBody CreateAccommodationRequest request) {
        return ApiResponse.ok(accommodationService.create(principal.id(), tripRid, request));
    }

    @GetMapping("/{rid}")
    public ApiResponse<AccommodationResponse> get(@CurrentUser AuthPrincipal principal,
                                                  @PathVariable String tripRid,
                                                  @PathVariable String rid) {
        return ApiResponse.ok(accommodationService.get(principal.id(), tripRid, rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<AccommodationResponse> update(@CurrentUser AuthPrincipal principal,
                                                     @PathVariable String tripRid,
                                                     @PathVariable String rid,
                                                     @Valid @RequestBody UpdateAccommodationRequest request) {
        return ApiResponse.ok(accommodationService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        accommodationService.delete(principal.id(), tripRid, rid);
    }
}
