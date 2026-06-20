package com.travelmate.transport;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.transport.dto.CreateTransportRequest;
import com.travelmate.transport.dto.TransportResponse;
import com.travelmate.transport.dto.UpdateTransportRequest;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Transport legs CRUD (SPEC §7 Module 6). All access goes through {@link TripAccessGuard}. */
@Service
public class TransportService {

    private final TransportRepository transportRepository;
    private final TripAccessGuard guard;

    public TransportService(TransportRepository transportRepository, TripAccessGuard guard) {
        this.transportRepository = transportRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<TransportResponse> list(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return transportRepository.findByTripIdOrderByDepartureTimeAsc(trip.getId()).stream()
                .map(TransportResponse::from)
                .toList();
    }

    @Transactional
    public TransportResponse create(Long userId, String tripRid, CreateTransportRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        validateTimes(request.departureTime(), request.arrivalTime());

        Transport t = new Transport();
        t.setTripId(trip.getId());
        t.setTransportType(request.transportType());
        t.setProvider(request.provider());
        t.setBookingCode(request.bookingCode());
        t.setDeparturePlace(request.departurePlace());
        t.setArrivalPlace(request.arrivalPlace());
        t.setDepartureTime(request.departureTime());
        t.setArrivalTime(request.arrivalTime());
        t.setQrData(request.qrData());
        t.setNote(request.note());
        return TransportResponse.from(transportRepository.save(t));
    }

    @Transactional(readOnly = true)
    public TransportResponse get(Long userId, String tripRid, String transportRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return TransportResponse.from(loadInTrip(transportRid, trip.getId()));
    }

    @Transactional
    public TransportResponse update(Long userId, String tripRid, String transportRid,
                                    UpdateTransportRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Transport t = loadInTrip(transportRid, trip.getId());
        if (request.transportType() != null) {
            t.setTransportType(request.transportType());
        }
        if (request.provider() != null) {
            t.setProvider(blankToNull(request.provider()));
        }
        if (request.bookingCode() != null) {
            t.setBookingCode(blankToNull(request.bookingCode()));
        }
        if (request.departurePlace() != null) {
            t.setDeparturePlace(blankToNull(request.departurePlace()));
        }
        if (request.arrivalPlace() != null) {
            t.setArrivalPlace(blankToNull(request.arrivalPlace()));
        }
        if (request.departureTime() != null) {
            t.setDepartureTime(request.departureTime());
        }
        if (request.arrivalTime() != null) {
            t.setArrivalTime(request.arrivalTime());
        }
        if (request.qrData() != null) {
            t.setQrData(blankToNull(request.qrData()));
        }
        if (request.note() != null) {
            t.setNote(blankToNull(request.note()));
        }
        validateTimes(t.getDepartureTime(), t.getArrivalTime());
        return TransportResponse.from(t);
    }

    @Transactional
    public void delete(Long userId, String tripRid, String transportRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        loadInTrip(transportRid, trip.getId()).setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Transport loadInTrip(String transportRid, Long tripId) {
        Transport t = transportRepository.findByRid(transportRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Transport not found."));
        if (!tripId.equals(t.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Transport not found.");
        }
        return t;
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private static void validateTimes(Instant departure, Instant arrival) {
        if (departure != null && arrival != null && arrival.isBefore(departure)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Arrival must be on or after departure.");
        }
    }
}
