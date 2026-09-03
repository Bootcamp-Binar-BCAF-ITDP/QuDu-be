package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.LoanReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final LoanApplicationService applicationService;
    private final LoanReviewService loanReviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> marketingBucket(
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseUtil.success(
                "Marketing bucket retrieved",
                applicationService.listMarketingBucket(pageable));
    }

    /** Marketing user accepts or rejects an application in the CHECKING bucket. */
    @PostMapping
    public ResponseEntity<ApiResponse<LoanReviewResponse>> submit(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody LoanReviewRequest request
    ) {
        return ResponseUtil.created(
                "Review submitted",
                loanReviewService.submitReview(appUser.getUserId(), request));
    }
}