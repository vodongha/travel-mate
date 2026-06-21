package com.travelmate.ticket;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.ticket.dto.CreateTicketRequest;
import com.travelmate.ticket.dto.TicketResponse;
import com.travelmate.ticket.dto.UpdateTicketRequest;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.TripAccessGuard;
import com.travelmate.trip.TripAccessGuard.TripContext;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-member tickets (a member shows their own ticket QR at the gate). Read is open to any member;
 * a member may manage <b>their own</b> tickets regardless of role, while managing someone else's
 * (or assigning to another member) needs EDITOR — so an organiser can hand out tickets, and an
 * individual can keep their own up to date.
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard guard;

    public TicketService(TicketRepository ticketRepository,
                         TripMemberRepository tripMemberRepository,
                         TripAccessGuard guard) {
        this.ticketRepository = ticketRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Map<Long, TripMember> members = memberMap(ctx.trip().getId());
        return ticketRepository.findByTripIdOrderByTicketTypeAscIdAsc(ctx.trip().getId()).stream()
                .map(t -> toResponse(t, members, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listMine(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Map<Long, TripMember> members = memberMap(ctx.trip().getId());
        return ticketRepository
                .findByTripIdAndMemberIdOrderByTicketTypeAscIdAsc(ctx.trip().getId(), me).stream()
                .map(t -> toResponse(t, members, me))
                .toList();
    }

    @Transactional
    public TicketResponse create(Long userId, String tripRid, CreateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        // No member given → the ticket is the caller's own (any role); a given member needs the check.
        Long targetMemberId = (request.memberRid() == null || request.memberRid().isBlank())
                ? ctx.membership().getId()
                : requireMemberId(request.memberRid(), ctx.trip().getId());
        requireManagePermission(ctx, targetMemberId);

        Ticket ticket = new Ticket();
        ticket.setTripId(ctx.trip().getId());
        ticket.setMemberId(targetMemberId);
        ticket.setTitle(request.title().trim());
        if (request.ticketType() != null) {
            ticket.setTicketType(request.ticketType());
        }
        ticket.setQrData(request.qrData());
        ticket.setSeat(request.seat());
        ticket.setNote(request.note());
        ticket = ticketRepository.save(ticket);
        return toResponse(ticket, memberMap(ctx.trip().getId()), ctx.membership().getId());
    }

    @Transactional
    public TicketResponse update(Long userId, String tripRid, String ticketRid, UpdateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Ticket ticket = loadInTrip(ticketRid, ctx.trip().getId());
        requireManagePermission(ctx, ticket.getMemberId());

        if (request.memberRid() != null && !request.memberRid().isBlank()) {
            Long newMemberId = requireMemberId(request.memberRid(), ctx.trip().getId());
            requireManagePermission(ctx, newMemberId); // reassigning to someone else needs EDITOR
            ticket.setMemberId(newMemberId);
        }
        if (request.title() != null) {
            ticket.setTitle(request.title().trim());
        }
        if (request.ticketType() != null) {
            ticket.setTicketType(request.ticketType());
        }
        if (request.qrData() != null && !request.qrData().isBlank()) {
            ticket.setQrData(request.qrData());
        }
        if (request.seat() != null) {
            ticket.setSeat(request.seat().isBlank() ? null : request.seat());
        }
        if (request.note() != null) {
            ticket.setNote(request.note().isBlank() ? null : request.note());
        }
        return toResponse(ticket, memberMap(ctx.trip().getId()), ctx.membership().getId());
    }

    @Transactional
    public void delete(Long userId, String tripRid, String ticketRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Ticket ticket = loadInTrip(ticketRid, ctx.trip().getId());
        requireManagePermission(ctx, ticket.getMemberId());
        ticket.setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Manage your own ticket at any role; manage another member's (or assign to them) needs EDITOR. */
    private void requireManagePermission(TripContext ctx, Long ticketMemberId) {
        boolean mine = ctx.membership().getId().equals(ticketMemberId);
        if (!mine && !ctx.membership().getRole().satisfies(MemberRole.EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Managing another member's ticket requires the EDITOR role.");
        }
    }

    private Ticket loadInTrip(String ticketRid, Long tripId) {
        Ticket ticket = ticketRepository.findByRid(ticketRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Ticket not found."));
        if (!tripId.equals(ticket.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Ticket not found.");
        }
        return ticket;
    }

    private Long requireMemberId(String memberRid, Long tripId) {
        TripMember member = tripMemberRepository.findByRid(memberRid)
                .filter(m -> tripId.equals(m.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Ticket owner is not a member of this trip."));
        return member.getId();
    }

    private Map<Long, TripMember> memberMap(Long tripId) {
        return tripMemberRepository.findByTripId(tripId).stream()
                .collect(Collectors.toMap(TripMember::getId, m -> m));
    }

    private TicketResponse toResponse(Ticket t, Map<Long, TripMember> members, Long me) {
        TripMember m = members.get(t.getMemberId());
        return TicketResponse.from(t,
                m == null ? null : m.getRid(),
                m == null ? null : m.getDisplayName(),
                t.getMemberId().equals(me));
    }
}
