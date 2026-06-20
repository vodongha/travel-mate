package com.travelmate.expense;

import com.travelmate.common.entity.BaseEntity;
import com.travelmate.common.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A real expense (SPEC §7 Module 9). The exchange rate is snapshotted at spend time and
 * {@code amountBase = amount * exchangeRate} is stored, never recomputed (SPEC §2.4). {@code payerId}
 * references TRIP_MEMBERS.ID (member-centric). When {@code paidFromFund} is true the expense is a
 * fund spend and creates no personal debt (no shares) — see Module 10/11.
 */
@Entity
@Table(name = "EXPENSES")
@SQLRestriction("IS_DELETED = 0")
public class Expense extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "EXPENSE_TYPE", nullable = false, length = 20)
    private ExpenseType expenseType = ExpenseType.PLANNED;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "EXCHANGE_RATE", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "AMOUNT_BASE", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountBase;

    @Column(name = "PAYER_ID", nullable = false)
    private Long payerId;

    @Column(name = "PLACE_ID")
    private Long placeId;

    @Column(name = "PAID_FROM_FUND", nullable = false)
    private boolean paidFromFund = false;

    @Column(name = "NOTE", length = 2000)
    private String note;

    @Column(name = "SPENT_AT", nullable = false)
    private Instant spentAt;

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

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public void setExpenseType(ExpenseType expenseType) {
        this.expenseType = expenseType;
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

    public Long getPayerId() {
        return payerId;
    }

    public void setPayerId(Long payerId) {
        this.payerId = payerId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public boolean isPaidFromFund() {
        return paidFromFund;
    }

    public void setPaidFromFund(boolean paidFromFund) {
        this.paidFromFund = paidFromFund;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getSpentAt() {
        return spentAt;
    }

    public void setSpentAt(Instant spentAt) {
        this.spentAt = spentAt;
    }
}
