package com.travelmate.fund.dto;

import com.travelmate.fund.FundContribution;

import java.math.BigDecimal;

public record ContributionResponse(
        String rid,
        String memberRid,
        String currency,
        BigDecimal amount,
        BigDecimal exchangeRate,
        BigDecimal amountBase,
        String note) {

    public static ContributionResponse from(FundContribution c, String memberRid) {
        return new ContributionResponse(
                c.getRid(), memberRid, c.getCurrency(), c.getAmount(),
                c.getExchangeRate(), c.getAmountBase(), c.getNote());
    }
}
