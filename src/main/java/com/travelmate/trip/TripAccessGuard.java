package com.travelmate.trip;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * The single place trip-scoped authorization lives (SPEC §5.2). Every trip-scoped operation
 * resolves access through this guard — never re-implement the membership/role check per service.
 *
 * <p>Rules:
 * <ul>
 *   <li>Trip missing, or the caller is not a member → uniform <b>404</b> (no existence leak — the
 *       same response whether the trip doesn't exist or simply isn't yours).</li>
 *   <li>Member but role below the requirement → <b>403</b>.</li>
 * </ul>
 *
 * For child resources, load the child, then call {@link #requireByTripId} with its {@code tripId}.
 */
@Component
public class TripAccessGuard {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;

    public TripAccessGuard(TripRepository tripRepository, TripMemberRepository tripMemberRepository) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
    }

    /** Resolved trip + the caller's membership in it. */
    public record TripContext(Trip trip, TripMember membership) {
    }

    public TripContext requireByTripRid(String tripRid, Long userId, MemberRole minRole) {
        Trip trip = tripRepository.findByRid(tripRid).orElseThrow(TripAccessGuard::notFound);
        return check(trip, userId, minRole);
    }

    public TripContext requireByTripId(Long tripId, Long userId, MemberRole minRole) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripAccessGuard::notFound);
        return check(trip, userId, minRole);
    }

    private TripContext check(Trip trip, Long userId, MemberRole minRole) {
        TripMember membership = tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId)
                .orElseThrow(TripAccessGuard::notFound); // not a member => 404, no existence leak
        if (!membership.getRole().satisfies(minRole)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "This action requires the " + minRole + " role.");
        }
        return new TripContext(trip, membership);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Trip not found.");
    }
}
