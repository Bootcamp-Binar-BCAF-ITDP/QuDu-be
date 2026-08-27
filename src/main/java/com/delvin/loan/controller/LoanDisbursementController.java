package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanDisbursementRequest;
import com.delvin.loan.dto.response.loanresp.LoanDisbursementResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanDisbursementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan-disbursements")
public class LoanDisbursementController {

    private final LoanDisbursementService disbursementService;

    public LoanDisbursementController(LoanDisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LoanDisbursementResponse>> disburse(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody LoanDisbursementRequest request) {
        return ResponseUtil.created("Loan disbursed", disbursementService.disburse(
                appUser.getUserId(),
                request)
        );
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<ApiResponse<LoanDisbursementResponse>> get(@PathVariable String applicationId) {
        return ResponseUtil.success("Disbursement retrieved", disbursementService.getByApplication(applicationId));
    }
}
