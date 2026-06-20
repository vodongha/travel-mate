package com.travelmate.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Only the planned amount is mutable; change the category by deleting + recreating. */
public record UpdateBudgetRequest(
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal plannedAmount) {
}
