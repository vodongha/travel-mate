package com.travelmate.checklist;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** A trip checklist item (SPEC §7 Module 12). {@code assigneeId} is an optional TRIP_MEMBERS id. */
@Entity
@Table(name = "CHECKLIST_ITEMS")
@SQLRestriction("IS_DELETED = 0")
public class ChecklistItem extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    @Column(name = "COMPLETED", nullable = false)
    private boolean completed = false;

    @Column(name = "ASSIGNEE_ID")
    private Long assigneeId;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
