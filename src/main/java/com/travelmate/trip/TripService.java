package com.travelmate.trip;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.trip.TripAccessGuard.TripContext;
import com.travelmate.trip.dto.AddMemberRequest;
import com.travelmate.trip.dto.CreateTripRequest;
import com.travelmate.trip.dto.MemberResponse;
import com.travelmate.trip.dto.TripResponse;
import com.travelmate.trip.dto.UpdateMemberRequest;
import com.travelmate.trip.dto.UpdateTripRequest;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Trip CRUD and membership management (SPEC §7 Modules 2-3). All access goes through the guard. */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard guard;
    private final UserRepository userRepository;
    private final MemberMergeService memberMergeService;

    private final com.travelmate.notification.NotificationService notificationService;

    public TripService(TripRepository tripRepository,
                       TripMemberRepository tripMemberRepository,
                       TripAccessGuard guard,
                       UserRepository userRepository,
                       MemberMergeService memberMergeService,
                       com.travelmate.notification.NotificationService notificationService) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.guard = guard;
        this.userRepository = userRepository;
        this.memberMergeService = memberMergeService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TripResponse create(Long userId, CreateTripRequest request) {
        validateDates(request.startDate(), request.endDate());

        Trip trip = new Trip();
        trip.setName(request.name().trim());
        trip.setDescription(request.description());
        trip.setDestination(request.destination());
        if (request.tripType() != null) {
            trip.setTripType(request.tripType());
        }
        trip.setStartDate(request.startDate());
        trip.setEndDate(request.endDate());
        if (request.timezone() != null) {
            trip.setTimezone(request.timezone());
        }
        if (request.baseCurrency() != null) {
            trip.setBaseCurrency(request.baseCurrency().toUpperCase());
        }
        trip.setStatus(TripStatus.PLANNING);
        trip.setOwnerId(userId);
        trip = tripRepository.save(trip);

        // Creating a trip makes the creator its OWNER member (SPEC §6 Module 2).
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Account no longer exists."));
        TripMember ownerMember = new TripMember();
        ownerMember.setTripId(trip.getId());
        ownerMember.setUserId(userId);
        ownerMember.setDisplayName(owner.getName());
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setJoinedAt(Instant.now());
        tripMemberRepository.save(ownerMember);

        notificationService.rescheduleTrip(trip);
        return TripResponse.from(trip, MemberRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> listMine(Long userId) {
        return tripRepository.findTripsForUser(userId).stream()
                .map(trip -> TripResponse.from(trip,
                        TripAccessGuard.effectiveRole(trip, roleOf(trip.getId(), userId))))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        return TripResponse.from(ctx.trip(), ctx.effectiveRole());
    }

    @Transactional
    public TripResponse update(Long userId, String tripRid, UpdateTripRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR);
        Trip trip = ctx.trip();
        if (request.name() != null) {
            trip.setName(request.name().trim());
        }
        if (request.description() != null) {
            trip.setDescription(request.description().isBlank() ? null : request.description());
        }
        if (request.destination() != null) {
            trip.setDestination(request.destination().isBlank() ? null : request.destination());
        }
        if (request.tripType() != null) {
            trip.setTripType(request.tripType());
        }
        if (request.startDate() != null) {
            trip.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            trip.setEndDate(request.endDate());
        }
        if (request.timezone() != null) {
            trip.setTimezone(request.timezone());
        }
        if (request.baseCurrency() != null) {
            trip.setBaseCurrency(request.baseCurrency().toUpperCase());
        }
        if (request.status() != null) {
            trip.setStatus(request.status());
        }
        validateDates(trip.getStartDate(), trip.getEndDate());
        notificationService.rescheduleTrip(trip);
        return TripResponse.from(trip, ctx.effectiveRole());
    }

    @Transactional
    public void delete(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);
        ctx.trip().setDeleted(true);
    }

    // ── members ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        return tripMemberRepository.findByTripId(ctx.trip().getId()).stream()
                .map(m -> MemberResponse.from(m, m.getId().equals(me)))
                .toList();
    }

    @Transactional
    public MemberResponse addGhostMember(Long userId, String tripRid, AddMemberRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);
        final String email = normalizeEmail(request.email());
        final MemberRole role = request.role() != null ? request.role() : MemberRole.VIEWER;

        // If the email already belongs to a real account, link that account directly instead of
        // creating a duplicate ghost — one email = one person (auto-joins them to the trip).
        if (email != null) {
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null) {
                if (tripMemberRepository.existsByTripIdAndUserId(ctx.trip().getId(), existing.getId())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "This person is already a member of the trip.");
                }
                TripMember real = new TripMember();
                real.setTripId(ctx.trip().getId());
                real.setUserId(existing.getId());
                real.setDisplayName(request.displayName().trim());
                real.setEmail(email);
                real.setRole(role);
                real.setJoinedAt(Instant.now());
                return MemberResponse.from(tripMemberRepository.save(real));
            }
        }

        TripMember member = new TripMember();
        member.setTripId(ctx.trip().getId());
        member.setUserId(null); // ghost
        member.setDisplayName(request.displayName().trim());
        member.setEmail(email); // lets a later sign-up/invite auto-link this ghost
        member.setRole(role);
        return MemberResponse.from(tripMemberRepository.save(member));
    }

    @Transactional
    public MemberResponse updateMember(Long userId, String tripRid, String memberRid,
                                       UpdateMemberRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);
        TripMember member = loadMemberInTrip(memberRid, ctx.trip().getId());
        if (request.role() != null) {
            // The trip owner can't be demoted (keeps every trip with at least one owner).
            if (isTripOwner(member, ctx.trip()) && request.role() != MemberRole.OWNER) {
                throw new ApiException(ErrorCode.FORBIDDEN, "The trip owner's role cannot be changed.");
            }
            member.setRole(request.role());
        }
        // Name/email are editable only for a ghost — a real member's identity comes from their account.
        if (member.isGhost()) {
            if (request.displayName() != null && !request.displayName().isBlank()) {
                member.setDisplayName(request.displayName().trim());
            }
            if (request.email() != null) {
                member.setEmail(normalizeEmail(request.email()));
            }
        }
        return MemberResponse.from(member);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    @Transactional
    public void removeMember(Long userId, String tripRid, String memberRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);
        TripMember member = loadMemberInTrip(memberRid, ctx.trip().getId());
        if (isTripOwner(member, ctx.trip())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "The trip owner cannot be removed.");
        }
        member.setDeleted(true);
    }

    /**
     * Merge {@code sourceRid} (typically a ghost) into {@code targetRid}: move every money / ticket /
     * checklist reference onto the target, then soft-delete the source. OWNER only. The trip owner
     * cannot be the source (it must always survive).
     */
    @Transactional
    public MemberResponse mergeMember(Long userId, String tripRid, String sourceRid, String targetRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);
        TripMember source = loadMemberInTrip(sourceRid, ctx.trip().getId());
        TripMember target = loadMemberInTrip(targetRid, ctx.trip().getId());
        if (source.getId().equals(target.getId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cannot merge a member into itself.");
        }
        if (isTripOwner(source, ctx.trip())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "The trip owner cannot be merged away.");
        }
        memberMergeService.repoint(source.getId(), target.getId());
        source.setDeleted(true);
        return MemberResponse.from(target, target.getId().equals(ctx.membership().getId()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MemberRole roleOf(Long tripId, Long userId) {
        return tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .map(TripMember::getRole)
                .orElse(null);
    }

    private TripMember loadMemberInTrip(String memberRid, Long tripId) {
        TripMember member = tripMemberRepository.findByRid(memberRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Member not found."));
        if (!member.getTripId().equals(tripId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Member not found."); // uniform, no cross-trip leak
        }
        return member;
    }

    private static boolean isTripOwner(TripMember member, Trip trip) {
        return member.getUserId() != null && member.getUserId().equals(trip.getOwnerId());
    }

    private static void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "End date must be on or after start date.");
        }
    }
}
