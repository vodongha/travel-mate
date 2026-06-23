package com.travelmate.trip;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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

    /**
     * Resolved trip + the caller's membership + their <b>effective</b> role. Once a trip has ended,
     * every non-owner is read-only (effective role VIEWER) even if their stored role is higher — see
     * {@link #effectiveRole}. Use {@link #effectiveRole()} for permission/display, not the raw
     * membership role.
     */
    public record TripContext(Trip trip, TripMember membership, MemberRole effectiveRole) {
    }

    /**
     * The role to actually enforce/show: after the trip's end date has passed, a non-owner drops to
     * VIEWER (the trip is over — read-only for everyone but the owner). The stored role is untouched
     * (reversible, no migration); we just derive this on read.
     */
    public static MemberRole effectiveRole(Trip trip, MemberRole stored) {
        if (stored == MemberRole.OWNER) {
            return stored;
        }
        final LocalDate end = trip.getEndDate();
        return (end != null && end.isBefore(LocalDate.now())) ? MemberRole.VIEWER : stored;
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
        MemberRole effective = effectiveRole(trip, membership.getRole());
        if (!effective.satisfies(minRole)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "This action requires the " + minRole + " role.");
        }
        return new TripContext(trip, membership, effective);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Trip not found.");
    }
}
