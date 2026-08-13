package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.service.LoanReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan-reviews")
public class LoanReviewController {

    private final LoanReviewService reviewService;

    public LoanReviewController(LoanReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Marketing user accepts or rejects an application in the CHECKING bucket. */

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<ApiResponse<LoanReviewResponse>> get(@PathVariable String applicationId) {
        return ResponseUtil.success("Review retrieved", reviewService.getByApplication(applicationId));
    }
}
