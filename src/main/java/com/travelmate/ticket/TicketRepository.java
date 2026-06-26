package com.travelmate.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByTripIdOrderByTicketTypeAscIdAsc(Long tripId);

    List<Ticket> findByTripId(Long tripId);

    Optional<Ticket> findByRid(String rid);

    /** Admin search over the title; empty q matches all. */
    @Query("select t from Ticket t where :q = '' or lower(t.title) like lower(concat('%', :q, '%'))")
    Page<Ticket> search(@Param("q") String q, Pageable pageable);
}
