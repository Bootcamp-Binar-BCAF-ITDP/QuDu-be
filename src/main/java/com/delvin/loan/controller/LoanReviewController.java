package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.service.LoanReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan-reviews")
@RequiredArgsConstructor
public class LoanReviewController {

    private final LoanReviewService reviewService;

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<ApiResponse<LoanReviewResponse>> get(@PathVariable String applicationId) {
        return ResponseUtil.success("Review retrieved", reviewService.getByApplication(applicationId));
    }
}
