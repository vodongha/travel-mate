package com.travelmate.dashboard;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.dashboard.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trip dashboard (SPEC §7 Module 13). Read access requires trip membership. */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(@CurrentUser AuthPrincipal principal,
                                                    @PathVariable String tripRid) {
        return ApiResponse.ok(dashboardService.dashboard(principal.id(), tripRid));
    }
}
