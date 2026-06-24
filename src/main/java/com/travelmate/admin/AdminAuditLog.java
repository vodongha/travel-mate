package com.travelmate.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only record of an admin mutation (who did what, to which target, when). Written by
 * {@link AdminService} on every privileged change so admin actions are always traceable. Not a
 * {@code BaseEntity} — it is never soft-deleted or updated.
 */
@Entity
@Table(name = "ADMIN_AUDIT_LOG")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RID", nullable = false, length = 36)
    private String rid;

    @Column(name = "ACTOR_USER_ID")
    private Long actorUserId;

    @Column(name = "ACTION", nullable = false, length = 60)
    private String action;

    @Column(name = "TARGET_TYPE", length = 40)
    private String targetType;

    @Column(name = "TARGET_RID", length = 36)
    private String targetRid;

    @Lob
    @Column(name = "DETAIL")
    private String detail;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(Long actorUserId, String action, String targetType, String targetRid,
                         String detail) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetRid = targetRid;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        if (rid == null) {
            rid = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRid() {
        return rid;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetRid() {
        return targetRid;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
