package com.travelmate.transport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransportRepository extends JpaRepository<Transport, Long> {

    List<Transport> findByTripIdOrderByDepartureTimeAsc(Long tripId);

    Optional<Transport> findByRid(String rid);
}
