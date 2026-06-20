package com.travelmate.place;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.place.dto.CreatePlaceRequest;
import com.travelmate.place.dto.PlaceResponse;
import com.travelmate.place.dto.UpdatePlaceRequest;
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

/** Trip places (SPEC §7 Module 4). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public ApiResponse<List<PlaceResponse>> list(@CurrentUser AuthPrincipal principal,
                                                 @PathVariable String tripRid) {
        return ApiResponse.ok(placeService.list(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlaceResponse> create(@CurrentUser AuthPrincipal principal,
                                             @PathVariable String tripRid,
                                             @Valid @RequestBody CreatePlaceRequest request) {
        return ApiResponse.ok(placeService.create(principal.id(), tripRid, request));
    }

    @GetMapping("/{rid}")
    public ApiResponse<PlaceResponse> get(@CurrentUser AuthPrincipal principal,
                                          @PathVariable String tripRid,
                                          @PathVariable String rid) {
        return ApiResponse.ok(placeService.get(principal.id(), tripRid, rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<PlaceResponse> update(@CurrentUser AuthPrincipal principal,
                                             @PathVariable String tripRid,
                                             @PathVariable String rid,
                                             @Valid @RequestBody UpdatePlaceRequest request) {
        return ApiResponse.ok(placeService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        placeService.delete(principal.id(), tripRid, rid);
    }
}
