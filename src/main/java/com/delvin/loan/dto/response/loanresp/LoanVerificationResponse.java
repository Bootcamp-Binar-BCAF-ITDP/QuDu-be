package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoanVerificationResponse {
    private Integer verificationId;
    private String applicationId;
    private UserSummary verifiedBy;
    private String callStatus;
    private String verificationNote;
    private LocalDate verificationDate;
}
