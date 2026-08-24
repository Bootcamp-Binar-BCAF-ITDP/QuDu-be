package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> create(
            @Valid @RequestBody LoanApplicationCreateRequest request) {

        return ResponseUtil.created(
                "Loan application submitted",
                applicationService.createApplication(request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> getAllApplication(
            @RequestParam(required = false) List<String> status,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseUtil.success(
                "Loan Application retrieved",
                applicationService.getAllApplication(status, pageable));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> get(@PathVariable String applicationId) {
        return ResponseUtil.success(
                "Loan application retrieved",
                applicationService.getApplication(applicationId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> listByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseUtil.success(
                "Loan applications retrieved",
                applicationService.listByCustomer(customerId, pageable));
    }
}