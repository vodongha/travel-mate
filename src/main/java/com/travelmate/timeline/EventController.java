package com.travelmate.timeline;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.timeline.dto.CreateEventRequest;
import com.travelmate.timeline.dto.EventResponse;
import com.travelmate.timeline.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Trip timeline events (SPEC §7 Module 5). {@code ?from=&to=} optionally bounds the window. */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiResponse<List<EventResponse>> list(
            @CurrentUser AuthPrincipal principal,
            @PathVariable String tripRid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(eventService.list(principal.id(), tripRid, from, to));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(@CurrentUser AuthPrincipal principal,
                                             @PathVariable String tripRid,
                                             @Valid @RequestBody CreateEventRequest request) {
        return ApiResponse.ok(eventService.create(principal.id(), tripRid, request));
    }

    @GetMapping("/{rid}")
    public ApiResponse<EventResponse> get(@CurrentUser AuthPrincipal principal,
                                          @PathVariable String tripRid,
                                          @PathVariable String rid) {
        return ApiResponse.ok(eventService.get(principal.id(), tripRid, rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<EventResponse> update(@CurrentUser AuthPrincipal principal,
                                             @PathVariable String tripRid,
                                             @PathVariable String rid,
                                             @Valid @RequestBody UpdateEventRequest request) {
        return ApiResponse.ok(eventService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        eventService.delete(principal.id(), tripRid, rid);
    }
}
