package com.travelmate.expense.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.expense.ExpenseType;
import com.travelmate.expense.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Create an expense. The server computes {@code amountBase = amount * exchangeRate} (rate snapshot).
 * {@code exchangeRate} is optional: omitted, the server uses 1 when {@code currency} equals the trip
 * base currency, otherwise fetches the market rate (which the client may override by sending one).
 *
 * <p>When {@code paidFromFund} is false this is a personal expense and {@code splitType} +
 * {@code participants} are required (the split must cover the whole amount). When true it is a fund
 * spend with no shares and no personal debt; split fields are ignored.
 */
public record CreateExpenseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Category category,
        ExpenseType expenseType,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate,
        @NotBlank @Size(max = 36) String payerRid,
        @Size(max = 36) String placeRid,
        @Size(max = 36) String eventRid,
        boolean paidFromFund,
        @Size(max = 2000) String note,
        Instant spentAt,
        SplitType splitType,
        @Valid List<ExpenseShareInput> participants) {
}
