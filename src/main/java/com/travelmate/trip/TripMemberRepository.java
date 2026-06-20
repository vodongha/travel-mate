package com.travelmate.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    List<TripMember> findByTripId(Long tripId);

    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);

    Optional<TripMember> findByRid(String rid);

    boolean existsByTripIdAndUserId(Long tripId, Long userId);
}
