package com.travelmate.budget.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateBudgetRequest(
        @NotNull Category category,
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal plannedAmount) {
}
