package com.travelmate.trip;

import com.travelmate.auth.OpaqueTokens;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.trip.TripAccessGuard.TripContext;
import com.travelmate.trip.dto.AcceptInvitationResponse;
import com.travelmate.trip.dto.CreateInvitationRequest;
import com.travelmate.trip.dto.InvitationResponse;
import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Trip invitations: create a link/QR token (OWNER), and accept one (any authenticated user). */
@Service
public class InvitationService {

    private static final int DEFAULT_EXPIRY_HOURS = 168; // 7 days

    private final TripInvitationRepository invitationRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    private final TripAccessGuard guard;
    private final UserRepository userRepository;
    private final String publicUrl;

    public InvitationService(TripInvitationRepository invitationRepository,
                             TripMemberRepository tripMemberRepository,
                             TripRepository tripRepository,
                             TripAccessGuard guard,
                             UserRepository userRepository,
                             @Value("${app.public-url:http://localhost:8000}") String publicUrl) {
        this.invitationRepository = invitationRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripRepository = tripRepository;
        this.guard = guard;
        this.userRepository = userRepository;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public InvitationResponse create(Long userId, String tripRid, CreateInvitationRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.OWNER);

        TripInvitation invitation = new TripInvitation();
        invitation.setTripId(ctx.trip().getId());
        invitation.setToken(OpaqueTokens.newRawToken());
        invitation.setRole(request.role() != null ? request.role() : MemberRole.VIEWER);
        int hours = request.expiresInHours() != null ? request.expiresInHours() : DEFAULT_EXPIRY_HOURS;
        invitation.setExpiresAt(Instant.now().plus(Duration.ofHours(hours)));
        invitation.setMaxUses(request.maxUses() != null ? request.maxUses() : 1);
        invitation.setUsedCount(0);
        invitation = invitationRepository.save(invitation);

        return new InvitationResponse(
                invitation.getRid(),
                invitation.getToken(),
                inviteUrl(invitation.getToken()),
                invitation.getRole(),
                invitation.getExpiresAt(),
                invitation.getMaxUses(),
                invitation.getUsedCount());
    }

    @Transactional
    public AcceptInvitationResponse accept(Long userId, String token) {
        Instant now = Instant.now();
        TripInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid invitation."));
        Trip trip = tripRepository.findById(invitation.getTripId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid invitation."));

        // Already a member? Idempotent — don't consume a use.
        Optional<TripMember> existing =
                tripMemberRepository.findByTripIdAndUserId(invitation.getTripId(), userId);
        if (existing.isPresent()) {
            return new AcceptInvitationResponse(trip.getRid(), existing.get().getRole());
        }

        // Atomically claim a use (guards MAX_USES against races; never read-check-write).
        if (invitationRepository.claimOne(token, now) == 0) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Invitation is expired or fully used.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Account no longer exists."));

        // Ghost→real merge (Open Decision #3): if a ghost in this trip matches the user's email,
        // claim that row IN PLACE — reusing its id keeps any future money references valid, so no
        // re-pointing is ever needed. Otherwise create a fresh membership.
        TripMember member = findGhostToMerge(invitation.getTripId(), user)
                .map(ghost -> {
                    ghost.setUserId(userId);
                    ghost.setJoinedAt(now);
                    if (invitation.getRole().satisfies(ghost.getRole())) {
                        ghost.setRole(invitation.getRole());
                    }
                    return ghost;
                })
                .orElseGet(() -> {
                    TripMember created = new TripMember();
                    created.setTripId(invitation.getTripId());
                    created.setUserId(userId);
                    created.setDisplayName(user.getName());
                    created.setRole(invitation.getRole());
                    created.setJoinedAt(now);
                    return tripMemberRepository.save(created);
                });

        return new AcceptInvitationResponse(trip.getRid(), member.getRole());
    }

    private Optional<TripMember> findGhostToMerge(Long tripId, User user) {
        return tripMemberRepository.findByTripId(tripId).stream()
                .filter(TripMember::isGhost)
                .filter(m -> m.getDisplayName() != null
                        && m.getDisplayName().equalsIgnoreCase(user.getEmail()))
                .findFirst();
    }

    private String inviteUrl(String token) {
        // A plain string the client encodes into a QR code (SPEC §2.7 — no QR image stored).
        // Path must match the Flutter app route (AcceptInviteScreen at /join?token=...).
        return publicUrl + "/join?token=" + token;
    }
}
