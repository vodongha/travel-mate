package com.travelmate.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByTripIdOrderByTicketTypeAscIdAsc(Long tripId);

    List<Ticket> findByTripId(Long tripId);

    Optional<Ticket> findByRid(String rid);
}
