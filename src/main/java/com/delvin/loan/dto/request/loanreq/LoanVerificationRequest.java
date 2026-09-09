package com.delvin.loan.dto.request.loanreq;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class LoanVerificationRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    @NotBlank(message = "callStatus is required")
    private String callStatus;

    @NotBlank(message = "verificationNote is required")
    private String verificationNote;
}
