package com.travelmate.expense;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * One member's share of a personal expense (SPEC §7 Module 11). {@code shareBase} is in the trip's
 * base currency; the shares of an expense sum exactly to its {@code AMOUNT_BASE}. {@code memberId}
 * references TRIP_MEMBERS.ID.
 */
@Entity
@Table(name = "EXPENSE_SHARES")
@SQLRestriction("IS_DELETED = 0")
public class ExpenseShare extends BaseEntity {

    @Column(name = "EXPENSE_ID", nullable = false)
    private Long expenseId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "SHARE_BASE", nullable = false, precision = 19, scale = 4)
    private BigDecimal shareBase;

    public Long getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(Long expenseId) {
        this.expenseId = expenseId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public BigDecimal getShareBase() {
        return shareBase;
    }

    public void setShareBase(BigDecimal shareBase) {
        this.shareBase = shareBase;
    }
}
