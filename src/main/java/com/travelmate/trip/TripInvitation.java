package com.travelmate.trip;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** An invitation token embedded in a link/QR (SPEC §6 Module 3). Accept is an atomic UPDATE. */
@Entity
@Table(name = "TRIP_INVITATIONS")
@SQLRestriction("IS_DELETED = 0")
public class TripInvitation extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "TOKEN", nullable = false, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "MAX_USES", nullable = false)
    private Integer maxUses = 1;

    @Column(name = "USED_COUNT", nullable = false)
    private Integer usedCount = 0;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(Integer usedCount) {
        this.usedCount = usedCount;
    }
}
