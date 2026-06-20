package com.travelmate.user;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A hashed, single-use-ish opaque token (SPEC §6 Module 1, §5.1): email verification, password
 * reset, or a refresh token. Only the SHA-256 hash is stored — never the token itself. {@code
 * usedAt != null} marks it spent (consumed or rotated).
 */
@Entity
@Table(name = "AUTH_TOKENS")
@SQLRestriction("IS_DELETED = 0")
public class AuthToken extends BaseEntity {

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 20)
    private AuthTokenType type;

    @Column(name = "TOKEN", nullable = false, length = 255)
    private String token;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "USED_AT")
    private Instant usedAt;

    public AuthToken() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AuthTokenType getType() {
        return type;
    }

    public void setType(AuthTokenType type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isSpent() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
