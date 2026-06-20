package com.travelmate.fund.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.fund.FundExpense;

import java.math.BigDecimal;

public record FundExpenseResponse(
        String rid,
        String title,
        Category category,
        String currency,
        BigDecimal amount,
        BigDecimal exchangeRate,
        BigDecimal amountBase,
        String note) {

    public static FundExpenseResponse from(FundExpense e) {
        return new FundExpenseResponse(
                e.getRid(), e.getTitle(), e.getCategory(), e.getCurrency(),
                e.getAmount(), e.getExchangeRate(), e.getAmountBase(), e.getNote());
    }
}
