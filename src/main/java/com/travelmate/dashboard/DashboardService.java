package com.travelmate.dashboard;

import com.travelmate.budget.Budget;
import com.travelmate.budget.BudgetRepository;
import com.travelmate.common.money.MoneyService;
import com.travelmate.dashboard.dto.DashboardResponse;
import com.travelmate.dashboard.dto.DashboardResponse.NextEvent;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.fund.FundExpenseRepository;
import com.travelmate.fund.FundService;
import com.travelmate.timeline.Event;
import com.travelmate.timeline.EventRepository;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Trip dashboard (SPEC §7 Module 13): one aggregated read — countdown, total budget, total spent
 * (personal + fund, each counted once), fund balance, and the next event. Reuses the fund balance
 * formula from {@link FundService} so it can't drift.
 */
@Service
public class DashboardService {

    private final TripAccessGuard guard;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final FundExpenseRepository fundExpenseRepository;
    private final FundService fundService;
    private final EventRepository eventRepository;
    private final MoneyService moneyService;

    public DashboardService(TripAccessGuard guard,
                            BudgetRepository budgetRepository,
                            ExpenseRepository expenseRepository,
                            FundExpenseRepository fundExpenseRepository,
                            FundService fundService,
                            EventRepository eventRepository,
                            MoneyService moneyService) {
        this.guard = guard;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.fundExpenseRepository = fundExpenseRepository;
        this.fundService = fundService;
        this.eventRepository = eventRepository;
        this.moneyService = moneyService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();

        BigDecimal totalBudget = budgetRepository.findByTripIdOrderByCategoryAsc(trip.getId()).stream()
                .map(Budget::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Money spent = every expense (personal + fund-paid) + direct fund expenses, each once.
        BigDecimal totalSpent = moneyService.normalizeAmount(
                expenseRepository.sumAmountBaseByTrip(trip.getId())
                        .add(fundExpenseRepository.sumAmountBaseByTrip(trip.getId())));

        BigDecimal fundBalance = fundService.balance(userId, tripRid).balance();

        NextEvent nextEvent = eventRepository
                .findFirstByTripIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(trip.getId(), Instant.now())
                .map(DashboardService::toNextEvent)
                .orElse(null);

        return new DashboardResponse(
                trip.getBaseCurrency(),
                countdownDays(trip),
                moneyService.normalizeAmount(totalBudget),
                totalSpent,
                fundBalance,
                nextEvent);
    }

    private static Long countdownDays(Trip trip) {
        if (trip.getStartDate() == null) {
            return null;
        }
        LocalDate today = LocalDate.now(ZoneId.of(trip.getTimezone()));
        return ChronoUnit.DAYS.between(today, trip.getStartDate());
    }

    private static NextEvent toNextEvent(Event event) {
        return new NextEvent(event.getRid(), event.getTitle(), event.getEventType(), event.getStartTime());
    }
}
