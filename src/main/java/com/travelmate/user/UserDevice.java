package com.travelmate.user;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** A device's FCM push token for a user (SPEC §6 Module 1). FCM token is partial-unique. */
@Entity
@Table(name = "USER_DEVICES")
@SQLRestriction("IS_DELETED = 0")
public class UserDevice extends BaseEntity {

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "FCM_TOKEN", nullable = false, length = 500)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "PLATFORM", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "LAST_SEEN_AT")
    private Instant lastSeenAt;

    protected UserDevice() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
