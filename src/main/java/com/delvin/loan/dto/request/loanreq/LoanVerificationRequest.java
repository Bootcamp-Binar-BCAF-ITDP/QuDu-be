package com.delvin.loan.dto.request.loanreq;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

/** Body for a back-office user recording the outcome of a verification call. */
@Getter
@Setter
public class LoanVerificationRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    @NotBlank(message = "backOfficeUserId is required")
    private String backOfficeUserId;

    /** One of CallStatus.CAN_BE_CONTACTED / NADA_SAMBUNG_TIDAK_DIANGKAT / SALAH_SAMBUNG */
    @NotBlank(message = "callStatus is required")
    private String callStatus;

    @NotBlank(message = "verificationNote is required")
    private String verificationNote;
}
