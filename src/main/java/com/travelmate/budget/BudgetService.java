package com.travelmate.budget;

import com.travelmate.budget.dto.BudgetResponse;
import com.travelmate.budget.dto.CreateBudgetRequest;
import com.travelmate.budget.dto.UpdateBudgetRequest;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.money.MoneyService;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Budget CRUD (SPEC §7 Module 8). Planned amounts are in the trip's base currency. */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TripAccessGuard guard;
    private final MoneyService moneyService;

    public BudgetService(BudgetRepository budgetRepository, TripAccessGuard guard, MoneyService moneyService) {
        this.budgetRepository = budgetRepository;
        this.guard = guard;
        this.moneyService = moneyService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return budgetRepository.findByTripIdOrderByCategoryAsc(trip.getId()).stream()
                .map(BudgetResponse::from)
                .toList();
    }

    @Transactional
    public BudgetResponse create(Long userId, String tripRid, CreateBudgetRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        if (budgetRepository.existsByTripIdAndCategory(trip.getId(), request.category())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A budget for category " + request.category() + " already exists.");
        }
        Budget budget = new Budget();
        budget.setTripId(trip.getId());
        budget.setCategory(request.category());
        budget.setPlannedAmount(moneyService.normalizeAmount(request.plannedAmount()));
        return BudgetResponse.from(budgetRepository.save(budget));
    }

    @Transactional
    public BudgetResponse update(Long userId, String tripRid, String budgetRid, UpdateBudgetRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Budget budget = loadInTrip(budgetRid, trip.getId());
        budget.setPlannedAmount(moneyService.normalizeAmount(request.plannedAmount()));
        return BudgetResponse.from(budget);
    }

    @Transactional
    public void delete(Long userId, String tripRid, String budgetRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        loadInTrip(budgetRid, trip.getId()).setDeleted(true);
    }

    private Budget loadInTrip(String budgetRid, Long tripId) {
        Budget budget = budgetRepository.findByRid(budgetRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Budget not found."));
        if (!tripId.equals(budget.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Budget not found.");
        }
        return budget;
    }
}
