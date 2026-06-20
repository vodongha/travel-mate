package com.travelmate.fund.dto;

import com.travelmate.common.entity.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddFundExpenseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Category category,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate,
        @Size(max = 2000) String note) {
}
