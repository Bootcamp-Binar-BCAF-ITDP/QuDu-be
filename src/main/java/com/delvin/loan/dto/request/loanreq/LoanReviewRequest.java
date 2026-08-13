package com.delvin.loan.dto.request.loanreq;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoanReviewRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    /** RecommendationStatus.ACCEPT or RecommendationStatus.REJECT */
    @NotBlank(message = "recommendation is required")
    private String recommendation;

    @NotBlank(message = "reviewNote is required")
    private String reviewNote;
}