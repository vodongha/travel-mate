package com.travelmate.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TripInvitationRepository extends JpaRepository<TripInvitation, Long> {

    Optional<TripInvitation> findByToken(String token);

    /**
     * Atomically consume one use of an invitation (SPEC §6 Module 3): increment USED_COUNT only
     * while it's still valid (under MAX_USES and unexpired). Returns rows affected — 1 = claimed,
     * 0 = exhausted/expired/missing. Never read-check-write.
     */
    @Modifying
    @Query("UPDATE TripInvitation i SET i.usedCount = i.usedCount + 1 "
            + "WHERE i.token = :token AND i.deleted = false "
            + "AND i.usedCount < i.maxUses AND i.expiresAt > :now")
    int claimOne(@Param("token") String token, @Param("now") Instant now);
}
