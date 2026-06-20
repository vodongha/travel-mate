package com.travelmate.settlement.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-member net balances plus the minimised set of who-pays-whom transactions (SPEC §7 Module 11).
 * All amounts are in the trip base currency. The shared fund is reconciled separately (Module 10).
 */
public record SettlementResponse(
        String baseCurrency,
        List<Balance> balances,
        List<Transaction> transactions) {

    /** {@code net = paid - owed}; positive = is owed money, negative = owes. */
    public record Balance(
            String memberRid,
            String displayName,
            BigDecimal paid,
            BigDecimal owed,
            BigDecimal net) {
    }

    public record Transaction(
            String fromMemberRid,
            String fromName,
            String toMemberRid,
            String toName,
            BigDecimal amount) {
    }
}
