package com.travelmate.report;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.report.dto.ReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** End-of-trip report (SPEC §8). Read access requires trip membership. */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<ReportResponse> report(@CurrentUser AuthPrincipal principal,
                                              @PathVariable String tripRid) {
        return ApiResponse.ok(reportService.report(principal.id(), tripRid));
    }
}
