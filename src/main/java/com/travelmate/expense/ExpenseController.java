package com.travelmate.expense;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.expense.dto.CreateExpenseRequest;
import com.travelmate.expense.dto.ExpenseResponse;
import com.travelmate.expense.dto.UpdateExpenseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Trip expenses (SPEC §7 Modules 9, 11). Note: SPEC §4 wants an {@code Idempotency-Key} on
 * money-creating POSTs; that cross-cutting infrastructure is a separate slice (Open decision #2)
 * and is not wired here yet.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public ApiResponse<List<ExpenseResponse>> list(@CurrentUser AuthPrincipal principal,
                                                   @PathVariable String tripRid) {
        return ApiResponse.ok(expenseService.list(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> create(@CurrentUser AuthPrincipal principal,
                                               @PathVariable String tripRid,
                                               @Valid @RequestBody CreateExpenseRequest request) {
        return ApiResponse.ok(expenseService.create(principal.id(), tripRid, request));
    }

    @GetMapping("/{rid}")
    public ApiResponse<ExpenseResponse> get(@CurrentUser AuthPrincipal principal,
                                            @PathVariable String tripRid,
                                            @PathVariable String rid) {
        return ApiResponse.ok(expenseService.get(principal.id(), tripRid, rid));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<ExpenseResponse> update(@CurrentUser AuthPrincipal principal,
                                               @PathVariable String tripRid,
                                               @PathVariable String rid,
                                               @Valid @RequestBody UpdateExpenseRequest request) {
        return ApiResponse.ok(expenseService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        expenseService.delete(principal.id(), tripRid, rid);
    }
}
