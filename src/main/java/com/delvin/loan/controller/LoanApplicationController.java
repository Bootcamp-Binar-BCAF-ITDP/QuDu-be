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

    /** Applications waiting for a marketing review. */
    @GetMapping("/bucket/marketing")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> marketingBucket() {
        return ResponseUtil.success("Marketing bucket retrieved", applicationService.listMarketingBucket());
    }

    /** Applications waiting for the given branch manager's decision. */
    @GetMapping("/bucket/branch-manager")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> branchManagerBucket(
            @RequestParam String branchManagerUserId) {
        return ResponseUtil.success("Branch manager bucket retrieved",
                applicationService.listBranchManagerBucket(branchManagerUserId));
    }

    /** Applications waiting for the given back office user to call/verify. */
    @GetMapping("/bucket/back-office")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> backOfficeBucket(
            @RequestParam String backOfficeUserId) {
        return ResponseUtil.success("Back office bucket retrieved",
                applicationService.listBackOfficeBucket(backOfficeUserId));
    }

    /** Branch manager approves or rejects an application accepted by marketing. */
    @PutMapping("/branch-manager-decision")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> branchManagerDecision(
            @Valid @RequestBody BranchManagerDecisionRequest request) {
        return ResponseUtil.success("Branch manager decision recorded",
                applicationService.branchManagerDecision(request));
    }
}
