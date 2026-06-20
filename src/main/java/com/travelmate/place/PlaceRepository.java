package com.travelmate.place;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByTripIdOrderByNameAsc(Long tripId);

    Optional<Place> findByRid(String rid);
}
