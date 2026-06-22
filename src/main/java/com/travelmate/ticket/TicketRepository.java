package com.travelmate.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByTripIdOrderByTicketTypeAscIdAsc(Long tripId);

    List<Ticket> findByTripIdAndMemberIdOrderByTicketTypeAscIdAsc(Long tripId, Long memberId);

    /** Group tickets (no owner) — shared by the whole trip, surfaced in everyone's "mine" list. */
    List<Ticket> findByTripIdAndMemberIdIsNullOrderByTicketTypeAscIdAsc(Long tripId);

    /** A member's tickets — used to re-point them when two members are merged. */
    List<Ticket> findByMemberId(Long memberId);

    Optional<Ticket> findByRid(String rid);
}
