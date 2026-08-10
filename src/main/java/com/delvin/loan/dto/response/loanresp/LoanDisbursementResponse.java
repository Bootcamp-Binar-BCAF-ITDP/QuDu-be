package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class LoanDisbursementResponse {
    private Integer disburseId;
    private String applicationId;
    private UserSummary processedBy;
    private Integer verificationId;
    private BigDecimal disbursedAmount;
    private String bankName;
    private String accountNumber;
    private LocalDate disbursementDate;
    private String status;
}
