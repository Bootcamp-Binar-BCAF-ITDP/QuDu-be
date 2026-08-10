package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanVerificationRequest;
import com.delvin.loan.dto.response.loanresp.LoanVerificationResponse;
import com.delvin.loan.service.LoanVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loan-verifications")
public class LoanVerificationController {

    private final LoanVerificationService verificationService;

    public LoanVerificationController(LoanVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** Back office logs the outcome of a verification call. */
    @PostMapping
    public ResponseEntity<ApiResponse<LoanVerificationResponse>> submit(
            @Valid @RequestBody LoanVerificationRequest request) {
        return ResponseUtil.created("Verification call recorded", verificationService.submitVerification(request));
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<ApiResponse<List<LoanVerificationResponse>>> list(@PathVariable String applicationId) {
        return ResponseUtil.success("Verification history retrieved",
                verificationService.listByApplication(applicationId));
    }
}
