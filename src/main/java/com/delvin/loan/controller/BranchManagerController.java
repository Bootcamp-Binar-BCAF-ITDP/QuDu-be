package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.plafond.PlafondDecisionRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.BranchManagerService;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bm")
@RequiredArgsConstructor
public class BranchManagerController {

    private final LoanApplicationService applicationService;
    private final BranchManagerService  branchManagerService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> branchManagerBucket(
            @AuthenticationPrincipal AppUser appUser,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseUtil.success(
                "Branch manager bucket retrieved",
                branchManagerService.listBranchManagerBucket(appUser.getUserId(), pageable)
        );
    }

    @PutMapping("/decision")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> branchManagerDecision(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody BranchManagerDecisionRequest request
    ) {

        return ResponseUtil.success(
                "Branch manager decision recorded",
                branchManagerService.branchManagerDecision(
                        appUser.getUserId(),
                        request
                )
        );
    }

    // PLAFOND
    @GetMapping("/plafond-requests")
    public ResponseEntity<ApiResponse<PageResponse<PlafondRequestResponse>>> plafondRequestBucket(
            @PageableDefault(size = 10, sort = "requestDate", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseUtil.success(
                "Plafond request bucket retrieved",
                branchManagerService.listPlafondRequestBucket(pageable)
        );
    }

    @PutMapping("/plafond-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<PlafondRequestResponse>> plafondDecision(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String requestId,
            @Valid @RequestBody PlafondDecisionRequest decision
    ) {
        return ResponseUtil.success(
                "Plafond decision recorded",
                branchManagerService.decidePlafondRequest(appUser.getUserId(), requestId, decision)
        );
    }
}
