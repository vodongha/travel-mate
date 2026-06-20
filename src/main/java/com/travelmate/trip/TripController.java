package com.travelmate.trip;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.trip.dto.CreateTripRequest;
import com.travelmate.trip.dto.TripResponse;
import com.travelmate.trip.dto.UpdateTripRequest;
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

/** Trip CRUD (SPEC §7 Module 2). Authorization is enforced in the service via TripAccessGuard. */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripResponse> create(@CurrentUser AuthPrincipal principal,
                                            @Valid @RequestBody CreateTripRequest request) {
        return ApiResponse.ok(tripService.create(principal.id(), request));
    }

    @GetMapping
    public ApiResponse<List<TripResponse>> listMine(@CurrentUser AuthPrincipal principal) {
        return ApiResponse.ok(tripService.listMine(principal.id()));
    }

    @GetMapping("/{rid}")
    public ApiResponse<TripResponse> get(@CurrentUser AuthPrincipal principal,
                                         @PathVariable String rid) {
        return ApiResponse.ok(tripService.get(principal.id(), rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<TripResponse> update(@CurrentUser AuthPrincipal principal,
                                            @PathVariable String rid,
                                            @Valid @RequestBody UpdateTripRequest request) {
        return ApiResponse.ok(tripService.update(principal.id(), rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal, @PathVariable String rid) {
        tripService.delete(principal.id(), rid);
    }
}
