package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loan-applications")
public class LoanApplicationController {

    private final LoanApplicationService applicationService;

    public LoanApplicationController(LoanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** Customer creates a new loan application (income, purpose, requestedAmount, tenor). */
    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> create(
            @Valid @RequestBody LoanApplicationCreateRequest request) {
        return ResponseUtil.created("Loan application submitted", applicationService.createApplication(request));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> get(@PathVariable String applicationId) {
        return ResponseUtil.success("Loan application retrieved", applicationService.getApplication(applicationId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> listByCustomer(@PathVariable String customerId) {
        return ResponseUtil.success("Loan applications retrieved", applicationService.listByCustomer(customerId));
    }
}
