package com.travelmate.ticket;

import com.travelmate.accommodation.Accommodation;
import com.travelmate.accommodation.AccommodationRepository;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.expense.ItineraryKind;
import com.travelmate.ticket.dto.CreateTicketRequest;
import com.travelmate.ticket.dto.TicketResponse;
import com.travelmate.ticket.dto.UpdateTicketRequest;
import com.travelmate.timeline.Event;
import com.travelmate.timeline.EventRepository;
import com.travelmate.transport.Transport;
import com.travelmate.transport.TransportRepository;
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
import java.util.stream.Stream;

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
    private final EventRepository eventRepository;
    private final TransportRepository transportRepository;
    private final AccommodationRepository accommodationRepository;
    private final TripAccessGuard guard;

    public TicketService(TicketRepository ticketRepository,
                         TripMemberRepository tripMemberRepository,
                         EventRepository eventRepository,
                         TransportRepository transportRepository,
                         AccommodationRepository accommodationRepository,
                         TripAccessGuard guard) {
        this.ticketRepository = ticketRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.eventRepository = eventRepository;
        this.transportRepository = transportRepository;
        this.accommodationRepository = accommodationRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Map<Long, TripMember> members = memberMap(ctx.trip().getId());
        ItineraryRids itin = itineraryRids(ctx.trip().getId());
        return ticketRepository.findByTripIdOrderByTicketTypeAscIdAsc(ctx.trip().getId()).stream()
                .map(t -> toResponse(t, members, itin, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listMine(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Map<Long, TripMember> members = memberMap(ctx.trip().getId());
        // "Mine" = my own tickets plus any group ticket (shared, relevant to everyone at the gate).
        List<Ticket> mine =
                ticketRepository.findByTripIdAndMemberIdOrderByTicketTypeAscIdAsc(ctx.trip().getId(), me);
        List<Ticket> group =
                ticketRepository.findByTripIdAndMemberIdIsNullOrderByTicketTypeAscIdAsc(ctx.trip().getId());
        ItineraryRids itin = itineraryRids(ctx.trip().getId());
        return Stream.concat(mine.stream(), group.stream())
                .map(t -> toResponse(t, members, itin, me))
                .toList();
    }

    @Transactional
    public TicketResponse create(Long userId, String tripRid, CreateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        // shared → group ticket (no owner); else no member given → the caller's own (any role);
        // else the named member. A group/other-member ticket needs the manage-others (EDITOR) check.
        Long targetMemberId;
        if (Boolean.TRUE.equals(request.shared())) {
            targetMemberId = null;
        } else if (request.memberRid() == null || request.memberRid().isBlank()) {
            targetMemberId = ctx.membership().getId();
        } else {
            targetMemberId = requireMemberId(request.memberRid(), ctx.trip().getId());
        }
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
        applyItinerary(ticket, request.itineraryKind(), request.itineraryRid(), ctx.trip().getId());
        ticket = ticketRepository.save(ticket);
        return toResponse(ticket, memberMap(ctx.trip().getId()),
                itineraryRids(ctx.trip().getId()), ctx.membership().getId());
    }

    @Transactional
    public TicketResponse update(Long userId, String tripRid, String ticketRid, UpdateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Ticket ticket = loadInTrip(ticketRid, ctx.trip().getId());
        requireManagePermission(ctx, ticket.getMemberId());

        if (Boolean.TRUE.equals(request.shared())) {
            requireManagePermission(ctx, null); // converting to a group ticket needs EDITOR
            ticket.setMemberId(null);
        } else if (request.memberRid() != null && !request.memberRid().isBlank()) {
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
        // A non-null itineraryRid (re)sets the link; blank clears it; omitted leaves it unchanged.
        if (request.itineraryRid() != null) {
            applyItinerary(ticket, request.itineraryKind(), request.itineraryRid(), ctx.trip().getId());
        }
        return toResponse(ticket, memberMap(ctx.trip().getId()),
                itineraryRids(ctx.trip().getId()), ctx.membership().getId());
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

    private TicketResponse toResponse(Ticket t, Map<Long, TripMember> members, ItineraryRids itin, Long me) {
        TripMember m = t.getMemberId() == null ? null : members.get(t.getMemberId());
        return TicketResponse.from(t,
                m == null ? null : m.getRid(),
                m == null ? null : m.getDisplayName(),
                me != null && me.equals(t.getMemberId()),
                itin.rid(t.getItineraryKind(), t.getItineraryId()));
    }

    private ItineraryRids itineraryRids(Long tripId) {
        return new ItineraryRids(
                eventRepository.findByTripIdOrderByStartTimeAsc(tripId).stream()
                        .collect(Collectors.toMap(Event::getId, Event::getRid)),
                transportRepository.findByTripIdOrderByDepartureTimeAsc(tripId).stream()
                        .collect(Collectors.toMap(Transport::getId, Transport::getRid)),
                accommodationRepository.findByTripIdOrderByCheckinTimeAsc(tripId).stream()
                        .collect(Collectors.toMap(Accommodation::getId, Accommodation::getRid)));
    }

    /** The rid maps for all three itinerary tables, so a polymorphic (kind, id) link resolves to a rid. */
    private record ItineraryRids(Map<Long, String> event, Map<Long, String> transport,
                                 Map<Long, String> accommodation) {
        String rid(ItineraryKind kind, Long id) {
            if (kind == null || id == null) {
                return null;
            }
            return switch (kind) {
                case EVENT -> event.get(id);
                case TRANSPORT -> transport.get(id);
                case ACCOMMODATION -> accommodation.get(id);
            };
        }
    }

    /**
     * Set (or clear) a ticket's polymorphic itinerary link — a blank rid clears it; otherwise the
     * (kind, rid) target is validated to belong to this trip before its id is stored.
     */
    private void applyItinerary(Ticket ticket, String kindStr, String rid, Long tripId) {
        if (rid == null || rid.isBlank()) {
            ticket.setItineraryKind(null);
            ticket.setItineraryId(null);
            return;
        }
        if (kindStr == null || kindStr.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "itineraryKind is required when itineraryRid is given.");
        }
        ItineraryKind kind;
        try {
            kind = ItineraryKind.valueOf(kindStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown itineraryKind: " + kindStr);
        }
        Long id = switch (kind) {
            case EVENT -> eventRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Event::getId).orElse(null);
            case TRANSPORT -> transportRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Transport::getId).orElse(null);
            case ACCOMMODATION -> accommodationRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Accommodation::getId).orElse(null);
        };
        if (id == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Itinerary item is not part of this trip.");
        }
        ticket.setItineraryKind(kind);
        ticket.setItineraryId(id);
    }
}
