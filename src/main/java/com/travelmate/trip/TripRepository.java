package com.travelmate.trip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByRid(String rid);

    /** Admin search over name/destination (case-insensitive); empty q matches all. */
    @Query("""
            select t from Trip t
            where :q = ''
               or lower(t.name) like lower(concat('%', :q, '%'))
               or lower(t.destination) like lower(concat('%', :q, '%'))
            """)
    Page<Trip> search(@Param("q") String q, Pageable pageable);

    /** Trips the user is a (live) member of, newest first. */
    @Query("SELECT t FROM Trip t WHERE t.id IN "
            + "(SELECT m.tripId FROM TripMember m WHERE m.userId = :userId) "
            + "ORDER BY t.createdAt DESC")
    List<Trip> findTripsForUser(@Param("userId") Long userId);
}
