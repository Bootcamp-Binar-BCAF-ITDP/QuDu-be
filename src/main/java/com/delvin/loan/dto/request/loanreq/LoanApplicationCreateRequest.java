package com.delvin.loan.dto.request.loanreq;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LoanApplicationCreateRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotNull(message = "requestedAmount is required")
    @DecimalMin(value = "0.01", message = "requestedAmount must be greater than 0")
    private BigDecimal requestedAmount;

    @NotNull(message = "tenor is required")
    @Min(value = 1, message = "tenor must be at least 1 month")
    private Integer tenor;

    @NotBlank(message = "purpose is required")
    private String purpose;

    @NotNull(message = "income is required")
    @DecimalMin(value = "0.01", message = "income must be greater than 0")
    private BigDecimal income;
}
