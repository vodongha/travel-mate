package com.travelmate.common.entity;

import com.travelmate.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Columns every table carries (SPEC §2.1). Concrete entities extend this; they must additionally
 * declare {@code @SQLRestriction("IS_DELETED = 0")} so soft-deleted rows are hidden by default —
 * that annotation cannot live on a {@code @MappedSuperclass} (it is per-entity), so it is the
 * entity author's responsibility. See SPEC §2.1 / Open Decision #1 on the read-soft-deleted case.
 *
 * <p>{@code ID} is the internal numeric key (joins/FKs only, never exposed). {@code RID} is the
 * public UUID v7 identifier used in URLs and responses, assigned on first persist.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "RID", updatable = false, nullable = false, length = 36)
    private String rid;

    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "IS_DELETED", nullable = false)
    private boolean deleted = false;

    @PrePersist
    void assignRid() {
        if (rid == null) {
            rid = UuidV7Generator.newRid();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRid() {
        return rid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Integer getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
