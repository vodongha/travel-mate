package com.travelmate.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    List<TripMember> findByTripId(Long tripId);

    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);

    Optional<TripMember> findByRid(String rid);

    boolean existsByTripIdAndUserId(Long tripId, Long userId);

    /** Live ghost memberships (no account yet) carrying this email — claimed when that user signs up. */
    List<TripMember> findByUserIdIsNullAndEmailIgnoreCase(String email);

    /**
     * Members by id <b>including soft-deleted ones</b> — a native query bypasses the
     * {@code @SQLRestriction} (Open decision #1). Settlement/report need a member who left the trip
     * but still has money history (payments/shares) so their name still resolves. Pass a non-empty
     * collection (Oracle rejects {@code IN ()}).
     */
    @Query(value = "SELECT * FROM TRIP_MEMBERS WHERE ID IN (:ids)", nativeQuery = true)
    List<TripMember> findAllByIdInIncludingDeleted(@Param("ids") Collection<Long> ids);
}
