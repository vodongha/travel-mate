package com.travelmate.expense.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.expense.ExpenseType;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update of an expense's metadata only — title, category, type, note, place, spent-at.
 * Changing the money (amount/currency/rate) or the split would require re-splitting; to keep shares
 * consistent, delete and recreate the expense for those. {@code placeRid} blank clears the link.
 */
public record UpdateExpenseRequest(
        @Size(max = 200) String title,
        Category category,
        ExpenseType expenseType,
        @Size(max = 36) String placeRid,
        @Size(max = 2000) String note,
        Instant spentAt) {
}
