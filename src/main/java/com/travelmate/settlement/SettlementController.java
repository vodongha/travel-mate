package com.travelmate.settlement;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.settlement.dto.SettlementResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settlement for a trip (SPEC §7 Module 11) — computed on the fly, read-only. */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public ApiResponse<SettlementResponse> settle(@CurrentUser AuthPrincipal principal,
                                                  @PathVariable String tripRid) {
        return ApiResponse.ok(settlementService.settle(principal.id(), tripRid));
    }
}
