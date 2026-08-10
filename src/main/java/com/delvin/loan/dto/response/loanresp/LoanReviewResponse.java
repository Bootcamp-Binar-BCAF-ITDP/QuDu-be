package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class LoanReviewResponse {
    private Integer reviewId;
    private String applicationId;
    private UserSummary marketing;
    private String recommendation;
    private String reviewNote;
    private LocalDate uploadedAt;
}
