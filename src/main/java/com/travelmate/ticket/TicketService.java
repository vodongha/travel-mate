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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tickets (a member shows their QR at the gate). A ticket covers one or more members (a shared
 * booking) via the TICKET_MEMBERS join, or none — a <em>group</em> ticket for the whole trip. You
 * may manage a ticket that is solely yours at any role; a group ticket, or one covering anyone but
 * you, needs EDITOR. Once the trip has ended only the owner can change anything (effective role).
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMemberRepository ticketMemberRepository;
    private final TripMemberRepository tripMemberRepository;
    private final EventRepository eventRepository;
    private final TransportRepository transportRepository;
    private final AccommodationRepository accommodationRepository;
    private final TripAccessGuard guard;

    public TicketService(TicketRepository ticketRepository,
                         TicketMemberRepository ticketMemberRepository,
                         TripMemberRepository tripMemberRepository,
                         EventRepository eventRepository,
                         TransportRepository transportRepository,
                         AccommodationRepository accommodationRepository,
                         TripAccessGuard guard) {
        this.ticketRepository = ticketRepository;
        this.ticketMemberRepository = ticketMemberRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.eventRepository = eventRepository;
        this.transportRepository = transportRepository;
        this.accommodationRepository = accommodationRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        return toResponses(
                ticketRepository.findByTripIdOrderByTicketTypeAscIdAsc(ctx.trip().getId()),
                ctx.trip().getId(), ctx.membership().getId());
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listMine(Long userId, String tripRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        List<Ticket> all = ticketRepository.findByTripIdOrderByTicketTypeAscIdAsc(ctx.trip().getId());
        Map<Long, List<Long>> byTicket = memberIdsByTicket(all);
        // "Mine" = tickets I'm on, plus group tickets (no members — relevant to everyone at the gate).
        List<Ticket> mine = all.stream()
                .filter(t -> {
                    List<Long> ids = byTicket.getOrDefault(t.getId(), List.of());
                    return ids.isEmpty() || ids.contains(me);
                })
                .toList();
        return toResponses(mine, ctx.trip().getId(), me);
    }

    @Transactional
    public TicketResponse create(Long userId, String tripRid, CreateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Set<Long> memberIds = resolveMemberSet(request.memberRids(),
                Boolean.TRUE.equals(request.shared()), me, ctx.trip().getId());
        requireCanAssign(ctx, memberIds);

        Ticket ticket = new Ticket();
        ticket.setTripId(ctx.trip().getId());
        ticket.setTitle(request.title().trim());
        if (request.ticketType() != null) {
            ticket.setTicketType(request.ticketType());
        }
        ticket.setQrData(request.qrData());
        ticket.setSeat(request.seat());
        ticket.setProvider(request.provider());
        ticket.setBookingCode(request.bookingCode());
        ticket.setNote(request.note());
        applyItinerary(ticket, request.itineraryKind(), request.itineraryRid(), ctx.trip().getId());
        ticket = ticketRepository.save(ticket);
        saveMembers(ticket.getId(), memberIds);
        return toResponse(ticket, new ArrayList<>(memberIds), memberMap(ctx.trip().getId()),
                itineraryRids(ctx.trip().getId()), me);
    }

    @Transactional
    public TicketResponse update(Long userId, String tripRid, String ticketRid, UpdateTicketRequest request) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Long me = ctx.membership().getId();
        Ticket ticket = loadInTrip(ticketRid, ctx.trip().getId());
        List<Long> current = ticketMemberRepository.findByTicketId(ticket.getId()).stream()
                .map(TicketMember::getMemberId).toList();
        requireCanManage(ctx, current);

        // Re-assign members if asked (shared → group/empty; else a new explicit set).
        Set<Long> memberIds = null;
        if (Boolean.TRUE.equals(request.shared())) {
            memberIds = new LinkedHashSet<>();
        } else if (request.memberRids() != null) {
            memberIds = resolveMemberSet(request.memberRids(), false, me, ctx.trip().getId());
        }
        if (memberIds != null) {
            requireCanAssign(ctx, memberIds);
            ticketMemberRepository.deleteByTicketId(ticket.getId());
            // Force the deletes to hit the DB before the re-inserts: Hibernate otherwise orders
            // inserts before deletes at flush, so re-assigning the same member would collide with
            // the still-present row on UK_TICKET_MEMBERS (ORA-00001).
            ticketMemberRepository.flush();
            saveMembers(ticket.getId(), memberIds);
        }

        if (request.title() != null) {
            ticket.setTitle(request.title().trim());
        }
        if (request.ticketType() != null) {
            ticket.setTicketType(request.ticketType());
        }
        if (request.qrData() != null) {
            ticket.setQrData(request.qrData().isBlank() ? null : request.qrData());
        }
        if (request.seat() != null) {
            ticket.setSeat(request.seat().isBlank() ? null : request.seat());
        }
        if (request.provider() != null) {
            ticket.setProvider(request.provider().isBlank() ? null : request.provider());
        }
        if (request.bookingCode() != null) {
            ticket.setBookingCode(request.bookingCode().isBlank() ? null : request.bookingCode());
        }
        if (request.note() != null) {
            ticket.setNote(request.note().isBlank() ? null : request.note());
        }
        if (request.itineraryRid() != null) {
            applyItinerary(ticket, request.itineraryKind(), request.itineraryRid(), ctx.trip().getId());
        }
        List<Long> finalIds = ticketMemberRepository.findByTicketId(ticket.getId()).stream()
                .map(TicketMember::getMemberId).toList();
        return toResponse(ticket, finalIds, memberMap(ctx.trip().getId()),
                itineraryRids(ctx.trip().getId()), me);
    }

    @Transactional
    public void delete(Long userId, String tripRid, String ticketRid) {
        TripContext ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Ticket ticket = loadInTrip(ticketRid, ctx.trip().getId());
        List<Long> current = ticketMemberRepository.findByTicketId(ticket.getId()).stream()
                .map(TicketMember::getMemberId).toList();
        requireCanManage(ctx, current);
        ticketMemberRepository.deleteByTicketId(ticket.getId());
        ticket.setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** group (shared) → empty set; else the named members; else the caller alone. Validates trip. */
    private Set<Long> resolveMemberSet(List<String> memberRids, boolean shared, Long me, Long tripId) {
        if (shared) {
            return new LinkedHashSet<>();
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (memberRids == null || memberRids.isEmpty()) {
            ids.add(me);
        } else {
            for (String rid : memberRids) {
                if (rid != null && !rid.isBlank()) {
                    ids.add(requireMemberId(rid, tripId));
                }
            }
            if (ids.isEmpty()) {
                ids.add(me);
            }
        }
        return ids;
    }

    private void saveMembers(Long ticketId, Set<Long> memberIds) {
        for (Long id : memberIds) {
            ticketMemberRepository.save(new TicketMember(ticketId, id));
        }
    }

    /** Manage a ticket that's solely yours at any role; a group/other-member ticket needs EDITOR. */
    private void requireCanManage(TripContext ctx, List<Long> memberIds) {
        boolean soleOwner = memberIds.size() == 1 && memberIds.contains(ctx.membership().getId());
        if (!soleOwner && !ctx.effectiveRole().satisfies(MemberRole.EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Managing this ticket requires the EDITOR role.");
        }
    }

    /** Assigning to exactly yourself is fine at any role; a group ticket or others needs EDITOR. */
    private void requireCanAssign(TripContext ctx, Set<Long> memberIds) {
        boolean soleSelf = memberIds.size() == 1 && memberIds.contains(ctx.membership().getId());
        if (!soleSelf && !ctx.effectiveRole().satisfies(MemberRole.EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Assigning a ticket to others or the whole group requires the EDITOR role.");
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

    private Map<Long, List<Long>> memberIdsByTicket(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tickets.stream().map(Ticket::getId).toList();
        return ticketMemberRepository.findByTicketIdIn(ids).stream()
                .collect(Collectors.groupingBy(TicketMember::getTicketId,
                        Collectors.mapping(TicketMember::getMemberId, Collectors.toList())));
    }

    private List<TicketResponse> toResponses(List<Ticket> tickets, Long tripId, Long me) {
        Map<Long, TripMember> members = memberMap(tripId);
        ItineraryRids itin = itineraryRids(tripId);
        Map<Long, List<Long>> byTicket = memberIdsByTicket(tickets);
        return tickets.stream()
                .map(t -> toResponse(t, byTicket.getOrDefault(t.getId(), List.of()), members, itin, me))
                .toList();
    }

    private TicketResponse toResponse(Ticket t, List<Long> memberIds, Map<Long, TripMember> members,
                                      ItineraryRids itin, Long me) {
        List<String> rids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Long id : memberIds) {
            TripMember m = members.get(id);
            if (m != null) {
                rids.add(m.getRid());
                names.add(m.getDisplayName());
            }
        }
        boolean mine = memberIds.isEmpty() || (me != null && memberIds.contains(me));
        return TicketResponse.from(t, rids, names, mine, itin.rid(t.getItineraryKind(), t.getItineraryId()));
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

    /** Set (or clear) a ticket's polymorphic itinerary link; blank rid clears it. */
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
