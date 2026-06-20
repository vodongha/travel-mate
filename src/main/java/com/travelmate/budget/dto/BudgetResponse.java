package com.travelmate.budget.dto;

import com.travelmate.budget.Budget;
import com.travelmate.common.entity.Category;

import java.math.BigDecimal;

public record BudgetResponse(
        String rid,
        Category category,
        BigDecimal plannedAmount) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getRid(), budget.getCategory(), budget.getPlannedAmount());
    }
}
