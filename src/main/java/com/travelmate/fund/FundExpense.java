package com.travelmate.fund;

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
 * Spending paid out of the shared fund (SPEC §7 Module 10). A fund expense creates no personal debt
 * (it is covered by everyone's contributions) — that is why fund spending is tracked here rather
 * than as a personal EXPENSE with shares. The rate is snapshotted and {@code amountBase} stored.
 */
@Entity
@Table(name = "FUND_EXPENSES")
@SQLRestriction("IS_DELETED = 0")
public class FundExpense extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 20)
    private Category category;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "EXCHANGE_RATE", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "AMOUNT_BASE", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountBase;

    @Column(name = "NOTE", length = 2000)
    private String note;

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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getAmountBase() {
        return amountBase;
    }

    public void setAmountBase(BigDecimal amountBase) {
        this.amountBase = amountBase;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
