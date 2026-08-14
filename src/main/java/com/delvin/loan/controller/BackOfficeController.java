package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bo")
public class BackOfficeController {

    private final LoanApplicationService applicationService;

    public BackOfficeController(LoanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> backOfficeBucket(
            @AuthenticationPrincipal AppUser appUser
    ) {
        return ResponseUtil.success("Back office bucket retrieved",
                applicationService.listBackOfficeBucket(appUser.getUserId()));
    }
}
