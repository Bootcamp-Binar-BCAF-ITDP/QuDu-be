package com.delvin.loan.dto.request.loanreq;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LoanDisbursementRequest {

    @NotBlank(message = "applicationId is required")
    private String applicationId;

    @NotNull(message = "approve is required")
    private Boolean approve;

    private String note;
}