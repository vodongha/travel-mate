package com.travelmate.budget;

import com.travelmate.common.entity.BaseEntity;
import com.travelmate.common.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * A planned budget for one category of a trip (SPEC §7 Module 8). {@code plannedAmount} is in the
 * trip's base currency. At most one live budget per (trip, category) — enforced by a partial unique
 * index in Flyway.
 */
@Entity
@Table(name = "BUDGETS")
@SQLRestriction("IS_DELETED = 0")
public class Budget extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 20)
    private Category category;

    @Column(name = "PLANNED_AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedAmount;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getPlannedAmount() {
        return plannedAmount;
    }

    public void setPlannedAmount(BigDecimal plannedAmount) {
        this.plannedAmount = plannedAmount;
    }
}
