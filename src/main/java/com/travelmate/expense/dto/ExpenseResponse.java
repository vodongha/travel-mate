package com.travelmate.expense.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Public view of an expense plus its shares (empty for a fund-paid expense). */
public record ExpenseResponse(
        String rid,
        String title,
        Category category,
        ExpenseType expenseType,
        String currency,
        BigDecimal amount,
        BigDecimal exchangeRate,
        BigDecimal amountBase,
        String payerRid,
        String placeRid,
        // Polymorphic itinerary link: kind (EVENT/TRANSPORT/ACCOMMODATION) + that item's rid, or both
        // null when the expense is standalone.
        String itineraryKind,
        String itineraryRid,
        boolean paidFromFund,
        String note,
        Instant spentAt,
        List<ShareView> shares) {

    public record ShareView(String memberRid, BigDecimal shareBase) {
    }

    public static ExpenseResponse from(Expense e, String payerRid, String placeRid,
                                       String itineraryRid, List<ShareView> shares) {
        return new ExpenseResponse(
                e.getRid(),
                e.getTitle(),
                e.getCategory(),
                e.getExpenseType(),
                e.getCurrency(),
                e.getAmount(),
                e.getExchangeRate(),
                e.getAmountBase(),
                payerRid,
                placeRid,
                e.getItineraryKind() == null ? null : e.getItineraryKind().name(),
                itineraryRid,
                e.isPaidFromFund(),
                e.getNote(),
                e.getSpentAt(),
                shares);
    }
}
