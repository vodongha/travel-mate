package com.travelmate.report;

import com.travelmate.budget.Budget;
import com.travelmate.budget.BudgetRepository;
import com.travelmate.common.entity.Category;
import com.travelmate.common.money.MoneyService;
import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.expense.ExpenseType;
import com.travelmate.fund.FundExpense;
import com.travelmate.fund.FundExpenseRepository;
import com.travelmate.report.dto.ReportResponse;
import com.travelmate.report.dto.ReportResponse.CategoryLine;
import com.travelmate.report.dto.ReportResponse.Summary;
import com.travelmate.report.dto.ReportResponse.UnexpectedExpense;
import com.travelmate.settlement.SettlementService;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * End-of-trip report (SPEC §8): budget vs actual overall and per category (shared {@link Category}
 * enum makes the two line up), the unexpected-expense list, and the minimised debts from the
 * settlement engine. Actual = personal + fund expenses, each counted once.
 */
@Service
public class ReportService {

    private final TripAccessGuard guard;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final FundExpenseRepository fundExpenseRepository;
    private final SettlementService settlementService;
    private final MoneyService moneyService;

    public ReportService(TripAccessGuard guard,
                         BudgetRepository budgetRepository,
                         ExpenseRepository expenseRepository,
                         FundExpenseRepository fundExpenseRepository,
                         SettlementService settlementService,
                         MoneyService moneyService) {
        this.guard = guard;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.fundExpenseRepository = fundExpenseRepository;
        this.settlementService = settlementService;
        this.moneyService = moneyService;
    }

    @Transactional(readOnly = true)
    public ReportResponse report(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();

        Map<Category, BigDecimal> budgetByCat = new EnumMap<>(Category.class);
        for (Budget b : budgetRepository.findByTripIdOrderByCategoryAsc(trip.getId())) {
            budgetByCat.merge(b.getCategory(), b.getPlannedAmount(), BigDecimal::add);
        }

        Map<Category, BigDecimal> actualByCat = new EnumMap<>(Category.class);
        List<Expense> expenses = expenseRepository.findByTripIdOrderBySpentAtDescIdDesc(trip.getId());
        List<UnexpectedExpense> unexpected = new ArrayList<>();
        for (Expense e : expenses) {
            actualByCat.merge(e.getCategory(), e.getAmountBase(), BigDecimal::add);
            if (e.getExpenseType() == ExpenseType.UNEXPECTED) {
                unexpected.add(new UnexpectedExpense(e.getRid(), e.getTitle(), e.getCategory(),
                        e.getExpenseType(), e.getAmountBase(), e.getSpentAt()));
            }
        }
        for (FundExpense fe : fundExpenseRepository.findByTripIdOrderByCreatedAtDescIdDesc(trip.getId())) {
            actualByCat.merge(fe.getCategory(), fe.getAmountBase(), BigDecimal::add);
        }

        List<CategoryLine> byCategory = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (Category category : unionKeys(budgetByCat, actualByCat)) {
            BigDecimal budget = moneyService.normalizeAmount(budgetByCat.getOrDefault(category, BigDecimal.ZERO));
            BigDecimal actual = moneyService.normalizeAmount(actualByCat.getOrDefault(category, BigDecimal.ZERO));
            byCategory.add(new CategoryLine(category, budget, actual, actual.subtract(budget)));
            totalBudget = totalBudget.add(budget);
            totalActual = totalActual.add(actual);
        }

        Summary summary = new Summary(totalBudget, totalActual, totalActual.subtract(totalBudget));
        return new ReportResponse(
                trip.getBaseCurrency(),
                summary,
                byCategory,
                unexpected,
                settlementService.settle(userId, tripRid).transactions());
    }

    private static java.util.Set<Category> unionKeys(Map<Category, ?> a, Map<Category, ?> b) {
        java.util.EnumSet<Category> keys = java.util.EnumSet.noneOf(Category.class);
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }
}
