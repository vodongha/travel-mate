package com.travelmate.report.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.expense.ExpenseType;
import com.travelmate.settlement.dto.SettlementResponse.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * End-of-trip report (SPEC §8). All money is in the trip's base currency. {@code overUnder =
 * actual - budget} (positive = over budget). {@code debts} is the minimised who-owes-whom list
 * reused from the settlement engine (Module 11).
 */
public record ReportResponse(
        String baseCurrency,
        Summary summary,
        List<CategoryLine> byCategory,
        List<UnexpectedExpense> unexpected,
        List<Transaction> debts) {

    public record Summary(BigDecimal totalBudget, BigDecimal totalActual, BigDecimal overUnder) {
    }

    public record CategoryLine(Category category, BigDecimal budget, BigDecimal actual, BigDecimal overUnder) {
    }

    public record UnexpectedExpense(String rid, String title, Category category, ExpenseType expenseType,
                                    BigDecimal amountBase, Instant spentAt) {
    }
}
