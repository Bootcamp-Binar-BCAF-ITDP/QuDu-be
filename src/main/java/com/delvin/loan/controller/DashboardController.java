package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.DashboardPeriod;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.response.dashboard.DashboardResponse;
import com.delvin.loan.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestParam(defaultValue = "THIS_MONTH") DashboardPeriod period
    ) {
        return ResponseUtil.success("Dashboard retrieved", dashboardService.getDashboard(period));
    }
}