package com.delvin.loan.dto.request.loanreq;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LoanDisbursementRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    @NotBlank(message = "backOfficeUserId is required")
    private String backOfficeUserId;

    @NotBlank(message = "bankName is required")
    private String bankName;

    @NotBlank(message = "accountNumber is required")
    private String accountNumber;

    /** Optional - defaults to the application's requestedAmount when omitted. */
    @DecimalMin(value = "0.01", message = "disbursedAmount must be greater than 0")
    private BigDecimal disbursedAmount;
}