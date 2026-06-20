package com.travelmate.fund;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.fund.dto.AddContributionRequest;
import com.travelmate.fund.dto.AddFundExpenseRequest;
import com.travelmate.fund.dto.ContributionResponse;
import com.travelmate.fund.dto.FundBalanceResponse;
import com.travelmate.fund.dto.FundExpenseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Shared fund: contributions, fund spending and the derived balance (SPEC §7 Module 10). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/fund")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping("/balance")
    public ApiResponse<FundBalanceResponse> balance(@CurrentUser AuthPrincipal principal,
                                                    @PathVariable String tripRid) {
        return ApiResponse.ok(fundService.balance(principal.id(), tripRid));
    }

    @GetMapping("/contributions")
    public ApiResponse<List<ContributionResponse>> listContributions(@CurrentUser AuthPrincipal principal,
                                                                     @PathVariable String tripRid) {
        return ApiResponse.ok(fundService.listContributions(principal.id(), tripRid));
    }

    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContributionResponse> addContribution(@CurrentUser AuthPrincipal principal,
                                                             @PathVariable String tripRid,
                                                             @Valid @RequestBody AddContributionRequest request) {
        return ApiResponse.ok(fundService.addContribution(principal.id(), tripRid, request));
    }

    @DeleteMapping("/contributions/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContribution(@CurrentUser AuthPrincipal principal,
                                   @PathVariable String tripRid,
                                   @PathVariable String rid) {
        fundService.deleteContribution(principal.id(), tripRid, rid);
    }

    @GetMapping("/expenses")
    public ApiResponse<List<FundExpenseResponse>> listFundExpenses(@CurrentUser AuthPrincipal principal,
                                                                   @PathVariable String tripRid) {
        return ApiResponse.ok(fundService.listFundExpenses(principal.id(), tripRid));
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FundExpenseResponse> addFundExpense(@CurrentUser AuthPrincipal principal,
                                                           @PathVariable String tripRid,
                                                           @Valid @RequestBody AddFundExpenseRequest request) {
        return ApiResponse.ok(fundService.addFundExpense(principal.id(), tripRid, request));
    }

    @DeleteMapping("/expenses/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFundExpense(@CurrentUser AuthPrincipal principal,
                                  @PathVariable String tripRid,
                                  @PathVariable String rid) {
        fundService.deleteFundExpense(principal.id(), tripRid, rid);
    }
}
