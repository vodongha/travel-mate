package com.travelmate.fund.dto;

import java.math.BigDecimal;

/**
 * Derived fund balance in the trip base currency (SPEC §7 Module 10), always computed by
 * aggregation, never stored: {@code balance = totalContributions - totalFundExpenses -
 * totalPersonalPaidFromFund}.
 */
public record FundBalanceResponse(
        String baseCurrency,
        BigDecimal totalContributions,
        BigDecimal totalFundExpenses,
        BigDecimal totalPersonalPaidFromFund,
        BigDecimal balance) {
}
