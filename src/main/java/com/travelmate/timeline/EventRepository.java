package com.travelmate.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByTripIdOrderByStartTimeAsc(Long tripId);

    /** Events whose start falls within [from, to], inclusive, ordered chronologically. */
    List<Event> findByTripIdAndStartTimeBetweenOrderByStartTimeAsc(Long tripId, Instant from, Instant to);

    Optional<Event> findByRid(String rid);
}
