package com.travelmate.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenAndType(String token, AuthTokenType type);

    /**
     * Revoke every still-live token of a type for a user — used on refresh-token reuse detection
     * (a flat token table can only revoke all of a user's sessions; see Open Decision #7) and when
     * issuing a fresh email-verify / password-reset token.
     */
    @Modifying
    @Query("UPDATE AuthToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
    int markAllUnusedAsUsed(@Param("userId") Long userId,
                            @Param("type") AuthTokenType type,
                            @Param("now") Instant now);
}
