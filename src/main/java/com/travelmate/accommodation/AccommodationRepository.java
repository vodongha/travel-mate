package com.travelmate.accommodation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    List<Accommodation> findByTripIdOrderByCheckinTimeAsc(Long tripId);

    Optional<Accommodation> findByRid(String rid);
}
