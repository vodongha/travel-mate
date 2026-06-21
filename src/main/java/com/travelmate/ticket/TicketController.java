package com.travelmate.ticket;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.ticket.dto.CreateTicketRequest;
import com.travelmate.ticket.dto.TicketResponse;
import com.travelmate.ticket.dto.UpdateTicketRequest;
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

/** Per-member tickets (SPEC §2.7 — QR stored as a string). {@code /mine} = the caller's own tickets. */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ApiResponse<List<TicketResponse>> list(@CurrentUser AuthPrincipal principal,
                                                  @PathVariable String tripRid) {
        return ApiResponse.ok(ticketService.list(principal.id(), tripRid));
    }

    @GetMapping("/mine")
    public ApiResponse<List<TicketResponse>> listMine(@CurrentUser AuthPrincipal principal,
                                                      @PathVariable String tripRid) {
        return ApiResponse.ok(ticketService.listMine(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TicketResponse> create(@CurrentUser AuthPrincipal principal,
                                              @PathVariable String tripRid,
                                              @Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.create(principal.id(), tripRid, request));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<TicketResponse> update(@CurrentUser AuthPrincipal principal,
                                              @PathVariable String tripRid,
                                              @PathVariable String rid,
                                              @Valid @RequestBody UpdateTicketRequest request) {
        return ApiResponse.ok(ticketService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        ticketService.delete(principal.id(), tripRid, rid);
    }
}
