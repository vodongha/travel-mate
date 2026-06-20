package com.travelmate.fund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddContributionRequest(
        @NotBlank @Size(max = 36) String memberRid,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate,
        @Size(max = 500) String note) {
}
