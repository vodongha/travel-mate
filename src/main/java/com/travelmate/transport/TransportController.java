package com.travelmate.transport;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.transport.dto.CreateTransportRequest;
import com.travelmate.transport.dto.TransportResponse;
import com.travelmate.transport.dto.UpdateTransportRequest;
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

/** Trip transport legs (SPEC §7 Module 6). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/transports")
public class TransportController {

    private final TransportService transportService;

    public TransportController(TransportService transportService) {
        this.transportService = transportService;
    }

    @GetMapping
    public ApiResponse<List<TransportResponse>> list(@CurrentUser AuthPrincipal principal,
                                                     @PathVariable String tripRid) {
        return ApiResponse.ok(transportService.list(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransportResponse> create(@CurrentUser AuthPrincipal principal,
                                                 @PathVariable String tripRid,
                                                 @Valid @RequestBody CreateTransportRequest request) {
        return ApiResponse.ok(transportService.create(principal.id(), tripRid, request));
    }

    @GetMapping("/{rid}")
    public ApiResponse<TransportResponse> get(@CurrentUser AuthPrincipal principal,
                                              @PathVariable String tripRid,
                                              @PathVariable String rid) {
        return ApiResponse.ok(transportService.get(principal.id(), tripRid, rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<TransportResponse> update(@CurrentUser AuthPrincipal principal,
                                                 @PathVariable String tripRid,
                                                 @PathVariable String rid,
                                                 @Valid @RequestBody UpdateTransportRequest request) {
        return ApiResponse.ok(transportService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        transportService.delete(principal.id(), tripRid, rid);
    }
}
