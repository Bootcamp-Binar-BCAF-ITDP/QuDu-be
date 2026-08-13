package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.LoanReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marketing")
public class MarketingController {

    private final LoanApplicationService applicationService;
    private final LoanReviewService loanReviewService;

    public MarketingController(LoanApplicationService applicationService, LoanReviewService loanReviewService) {
        this.applicationService = applicationService;
        this.loanReviewService = loanReviewService;

    }

    /** Applications waiting for a marketing review. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> marketingBucket() {
        return ResponseUtil.success("Marketing bucket retrieved", applicationService.listMarketingBucket());
    }

    /** Marketing user accepts or rejects an application in the CHECKING bucket. */
    @PostMapping
    public ResponseEntity<ApiResponse<LoanReviewResponse>> submit(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody LoanReviewRequest request
    ) {

        return ResponseUtil.created(
                "Review submitted",
                loanReviewService.submitReview(
                        appUser.getUserId(),
                        request
                )
        );
    }
}
