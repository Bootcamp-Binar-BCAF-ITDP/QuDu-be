package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class LoanApplicationResponse {
    private String applicationId;
    private CustomerSummary customer;
    private BigDecimal requestedAmount;
    private Integer tenor;
    private String purpose;
    private BigDecimal income;
    private String status;
    private LocalDate submissionDate;
    private List<LoanDocumentResponse> documents;
    private LoanReviewResponse review;
    private List<LoanVerificationResponse> verifications;
    private LoanDisbursementResponse disbursement;
}
