package com.travelmate.timeline;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.place.Place;
import com.travelmate.place.PlaceRepository;
import com.travelmate.place.PlaceService;
import com.travelmate.timeline.dto.CreateEventRequest;
import com.travelmate.timeline.dto.EventResponse;
import com.travelmate.timeline.dto.UpdateEventRequest;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Timeline events CRUD (SPEC §7 Module 5). All access goes through {@link TripAccessGuard}. */
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final PlaceRepository placeRepository;
    private final PlaceService placeService;
    private final TripAccessGuard guard;
    private final com.travelmate.notification.NotificationService notificationService;

    public EventService(EventRepository eventRepository,
                        PlaceRepository placeRepository,
                        PlaceService placeService,
                        TripAccessGuard guard,
                        com.travelmate.notification.NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.placeRepository = placeRepository;
        this.placeService = placeService;
        this.guard = guard;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(Long userId, String tripRid, Instant from, Instant to) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        List<Event> events = (from != null && to != null)
                ? eventRepository.findByTripIdAndStartTimeBetweenOrderByStartTimeAsc(trip.getId(), from, to)
                : eventRepository.findByTripIdOrderByStartTimeAsc(trip.getId());

        // Resolve place rids in one query rather than N (avoids an N+1 over the timeline).
        Map<Long, String> placeRids = placeRepository
                .findAllById(events.stream().map(Event::getPlaceId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Place::getId, Place::getRid));

        return events.stream()
                .map(e -> EventResponse.from(e, e.getPlaceId() == null ? null : placeRids.get(e.getPlaceId())))
                .toList();
    }

    @Transactional
    public EventResponse create(Long userId, String tripRid, CreateEventRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        validateTimes(request.startTime(), request.endTime());

        Event event = new Event();
        event.setTripId(trip.getId());
        event.setTitle(request.title().trim());
        if (request.eventType() != null) {
            event.setEventType(request.eventType());
        }
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setPlaceId(placeService.resolvePlaceId(request.placeRid(), trip.getId()));
        event.setNote(request.note());
        event = eventRepository.save(event);
        notificationService.rescheduleEvent(trip, event);
        return EventResponse.from(event, request.placeRid());
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long userId, String tripRid, String eventRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        Event event = loadInTrip(eventRid, trip.getId());
        return EventResponse.from(event, placeRidOf(event.getPlaceId()));
    }

    @Transactional
    public EventResponse update(Long userId, String tripRid, String eventRid, UpdateEventRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Event event = loadInTrip(eventRid, trip.getId());
        if (request.title() != null) {
            event.setTitle(request.title().trim());
        }
        if (request.eventType() != null) {
            event.setEventType(request.eventType());
        }
        if (request.startTime() != null) {
            event.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            event.setEndTime(request.endTime());
        }
        if (request.placeRid() != null) {
            final Long oldPlaceId = event.getPlaceId();
            // Blank clears the link; a value re-points it (validated against the trip).
            event.setPlaceId(request.placeRid().isBlank()
                    ? null
                    : placeService.resolvePlaceId(request.placeRid(), trip.getId()));
            // If the event let go of a place, drop that place when nothing else uses it.
            if (oldPlaceId != null && !oldPlaceId.equals(event.getPlaceId())) {
                pruneOrphanPlace(oldPlaceId, event.getId());
            }
        }
        if (request.note() != null) {
            event.setNote(request.note().isBlank() ? null : request.note());
        }
        validateTimes(event.getStartTime(), event.getEndTime());
        notificationService.rescheduleEvent(trip, event);
        return EventResponse.from(event, placeRidOf(event.getPlaceId()));
    }

    @Transactional
    public void delete(Long userId, String tripRid, String eventRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Event event = loadInTrip(eventRid, trip.getId());
        final Long placeId = event.getPlaceId();
        event.setDeleted(true);
        notificationService.cancelEvent(event.getId());
        // Deleting the event frees its place; drop the place when nothing else uses it.
        pruneOrphanPlace(placeId, event.getId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Soft-delete {@code placeId} when no live event other than {@code keepEventId} still references
     * it — so a place added via an event's location picker disappears from the Places tab once the
     * last event using it lets it go. Places still used elsewhere are kept. No-op for a null id.
     */
    private void pruneOrphanPlace(Long placeId, Long keepEventId) {
        if (placeId == null) {
            return;
        }
        if (!eventRepository.existsByPlaceIdAndIdNot(placeId, keepEventId)) {
            placeRepository.findById(placeId).ifPresent(p -> p.setDeleted(true));
        }
    }

    private Event loadInTrip(String eventRid, Long tripId) {
        Event event = eventRepository.findByRid(eventRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Event not found."));
        if (!tripId.equals(event.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Event not found.");
        }
        return event;
    }

    private String placeRidOf(Long placeId) {
        return placeId == null ? null
                : placeRepository.findById(placeId).map(Place::getRid).orElse(null);
    }

    private static void validateTimes(Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Event end must be on or after its start.");
        }
    }
}
