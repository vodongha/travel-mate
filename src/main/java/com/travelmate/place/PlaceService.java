package com.travelmate.place;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.place.dto.CreatePlaceRequest;
import com.travelmate.place.dto.PlaceResponse;
import com.travelmate.place.dto.UpdatePlaceRequest;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Places CRUD (SPEC §7 Module 4). All access goes through {@link TripAccessGuard}. */
@Service
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final TripAccessGuard guard;

    public PlaceService(PlaceRepository placeRepository, TripAccessGuard guard) {
        this.placeRepository = placeRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> list(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return placeRepository.findByTripIdOrderByNameAsc(trip.getId()).stream()
                .map(PlaceResponse::from)
                .toList();
    }

    @Transactional
    public PlaceResponse create(Long userId, String tripRid, CreatePlaceRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Place place = new Place();
        place.setTripId(trip.getId());
        place.setName(request.name().trim());
        place.setAddress(request.address());
        place.setLatitude(request.latitude());
        place.setLongitude(request.longitude());
        if (request.placeType() != null) {
            place.setPlaceType(request.placeType());
        }
        return PlaceResponse.from(placeRepository.save(place));
    }

    @Transactional(readOnly = true)
    public PlaceResponse get(Long userId, String tripRid, String placeRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return PlaceResponse.from(loadInTrip(placeRid, trip.getId()));
    }

    @Transactional
    public PlaceResponse update(Long userId, String tripRid, String placeRid, UpdatePlaceRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Place place = loadInTrip(placeRid, trip.getId());
        if (request.name() != null) {
            place.setName(request.name().trim());
        }
        if (request.address() != null) {
            place.setAddress(request.address().isBlank() ? null : request.address());
        }
        if (request.latitude() != null) {
            place.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            place.setLongitude(request.longitude());
        }
        if (request.placeType() != null) {
            place.setPlaceType(request.placeType());
        }
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(Long userId, String tripRid, String placeRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        loadInTrip(placeRid, trip.getId()).setDeleted(true);
    }

    /**
     * Resolve a place rid to its internal id, confirming it belongs to {@code tripId}. For other
     * modules (e.g. events referencing a place) to validate a place reference without leaking
     * cross-trip existence. Returns {@code null} for a null/blank rid (the reference is optional).
     */
    @Transactional(readOnly = true)
    public Long resolvePlaceId(String placeRid, Long tripId) {
        if (placeRid == null || placeRid.isBlank()) {
            return null;
        }
        return loadInTrip(placeRid, tripId).getId();
    }

    /** Load a place by rid and confirm it belongs to the trip (uniform 404, no cross-trip leak). */
    Place loadInTrip(String placeRid, Long tripId) {
        Place place = placeRepository.findByRid(placeRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Place not found."));
        if (!tripId.equals(place.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Place not found.");
        }
        return place;
    }
}
