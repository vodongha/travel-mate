package com.travelmate.accommodation;

import com.travelmate.accommodation.dto.AccommodationResponse;
import com.travelmate.accommodation.dto.CreateAccommodationRequest;
import com.travelmate.accommodation.dto.UpdateAccommodationRequest;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Accommodation CRUD (SPEC §7 Module 7). All access goes through {@link TripAccessGuard}. */
@Service
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final TripAccessGuard guard;

    public AccommodationService(AccommodationRepository accommodationRepository, TripAccessGuard guard) {
        this.accommodationRepository = accommodationRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<AccommodationResponse> list(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return accommodationRepository.findByTripIdOrderByCheckinTimeAsc(trip.getId()).stream()
                .map(AccommodationResponse::from)
                .toList();
    }

    @Transactional
    public AccommodationResponse create(Long userId, String tripRid, CreateAccommodationRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        validateTimes(request.checkinTime(), request.checkoutTime());

        Accommodation a = new Accommodation();
        a.setTripId(trip.getId());
        a.setName(request.name().trim());
        a.setBookingCode(request.bookingCode());
        a.setAddress(request.address());
        a.setCheckinTime(request.checkinTime());
        a.setCheckoutTime(request.checkoutTime());
        a.setQrData(request.qrData());
        a.setNote(request.note());
        return AccommodationResponse.from(accommodationRepository.save(a));
    }

    @Transactional(readOnly = true)
    public AccommodationResponse get(Long userId, String tripRid, String accommodationRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return AccommodationResponse.from(loadInTrip(accommodationRid, trip.getId()));
    }

    @Transactional
    public AccommodationResponse update(Long userId, String tripRid, String accommodationRid,
                                        UpdateAccommodationRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Accommodation a = loadInTrip(accommodationRid, trip.getId());
        if (request.name() != null) {
            a.setName(request.name().trim());
        }
        if (request.bookingCode() != null) {
            a.setBookingCode(blankToNull(request.bookingCode()));
        }
        if (request.address() != null) {
            a.setAddress(blankToNull(request.address()));
        }
        if (request.checkinTime() != null) {
            a.setCheckinTime(request.checkinTime());
        }
        if (request.checkoutTime() != null) {
            a.setCheckoutTime(request.checkoutTime());
        }
        if (request.qrData() != null) {
            a.setQrData(blankToNull(request.qrData()));
        }
        if (request.note() != null) {
            a.setNote(blankToNull(request.note()));
        }
        validateTimes(a.getCheckinTime(), a.getCheckoutTime());
        return AccommodationResponse.from(a);
    }

    @Transactional
    public void delete(Long userId, String tripRid, String accommodationRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        loadInTrip(accommodationRid, trip.getId()).setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Accommodation loadInTrip(String accommodationRid, Long tripId) {
        Accommodation a = accommodationRepository.findByRid(accommodationRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Accommodation not found."));
        if (!tripId.equals(a.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Accommodation not found.");
        }
        return a;
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private static void validateTimes(Instant checkin, Instant checkout) {
        if (checkin != null && checkout != null && checkout.isBefore(checkin)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Check-out must be on or after check-in.");
        }
    }
}
