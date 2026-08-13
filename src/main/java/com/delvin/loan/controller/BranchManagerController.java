package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bm")
public class BranchManagerController {

    private final LoanApplicationService applicationService;

    public BranchManagerController(LoanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> branchManagerBucket(
            @AuthenticationPrincipal AppUser appUser
    ) {

        return ResponseUtil.success(
                "Branch manager bucket retrieved",
                applicationService.listBranchManagerBucket(appUser.getUserId())
        );
    }

    @PutMapping("/decision")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> branchManagerDecision(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody BranchManagerDecisionRequest request
    ) {

        return ResponseUtil.success(
                "Branch manager decision recorded",
                applicationService.branchManagerDecision(
                        appUser.getUserId(),
                        request
                )
        );
    }
}
