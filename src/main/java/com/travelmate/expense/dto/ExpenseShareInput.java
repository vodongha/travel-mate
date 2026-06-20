package com.travelmate.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One participant in a split. {@code value} meaning depends on the expense's {@code splitType}:
 * ignored for EQUAL, an exact base amount for EXACT, a percent for PERCENT, a weight for SHARES.
 */
public record ExpenseShareInput(
        @NotBlank @Size(max = 36) String memberRid,
        BigDecimal value) {
}
