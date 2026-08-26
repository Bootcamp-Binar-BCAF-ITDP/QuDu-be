package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@RestController
@RequestMapping("/api/loan-applications")
@Slf4j
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
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseUtil.success(
                "Loan Application retrieved",
                applicationService.getAllApplication(status, search, pageable));
    }

    @GetMapping("/bucket")
    // @PreAuthorize("hasAnyRole('MARKETING','BRANCH_MANAGER','BACK_OFFICE')")
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> getMyBucket(
            @AuthenticationPrincipal AppUser appUser,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC) Pageable pageable) {

        if (appUser == null) {
            return ResponseUtil.error(HttpStatus.FORBIDDEN,
                    "Only internal users have an application bucket");
        }

        try {
            return ResponseUtil.success(
                    "Bucket retrieved",
                    applicationService.getMyBucket(
                            appUser.getUserId(), appUser.getRole(), pageable));

        } catch (BusinessException e) {
            return ResponseUtil.error(e.getStatus(), e.getMessage());

        } catch (PropertyReferenceException e) {
            return ResponseUtil.error(HttpStatus.BAD_REQUEST,
                    "Invalid sort property: " + e.getPropertyName());

        } catch (Exception e) {
            return ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve bucket");
        }
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> getApplicationById(@PathVariable String applicationId) {
        try {
            return ResponseUtil.success("Loan application retrieved", applicationService.getApplication(applicationId));
        } catch (HttpClientErrorException e) {
            return ResponseUtil.error(HttpStatus.NOT_FOUND, "Loan application not found");
        } catch (BusinessException e) {
            return ResponseUtil.error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR,"Failed to retrieve loan application");
        }
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> listByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseUtil.success(
                "Loan applications retrieved",
                applicationService.listByCustomer(customerId, pageable));
    }
}