package com.travelmate.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByRid(String rid);

    /** Trips the user is a (live) member of, newest first. */
    @Query("SELECT t FROM Trip t WHERE t.id IN "
            + "(SELECT m.tripId FROM TripMember m WHERE m.userId = :userId) "
            + "ORDER BY t.createdAt DESC")
    List<Trip> findTripsForUser(@Param("userId") Long userId);
}
