package com.travelmate.fund;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * A member's contribution into the shared fund (SPEC §7 Module 10). Multi-currency: the rate is
 * snapshotted and {@code amountBase} stored. {@code memberId} references TRIP_MEMBERS.ID.
 */
@Entity
@Table(name = "FUND_CONTRIBUTIONS")
@SQLRestriction("IS_DELETED = 0")
public class FundContribution extends BaseEntity {

    @Column(name = "TRIP_ID", nullable = false)
    private Long tripId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "EXCHANGE_RATE", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "AMOUNT_BASE", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountBase;

    @Column(name = "NOTE", length = 500)
    private String note;

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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
