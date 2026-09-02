package com.delvin.loan.dto.request.plafond;

import com.delvin.loan.common.PlafondRequestStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlafondDecisionRequest {

    @NotNull(message = "decision is required")
    private PlafondRequestStatus decision;

    @DecimalMin(value = "0.0", inclusive = false, message = "approvedAmount must be greater than 0")
    private BigDecimal approvedAmount;

    private String notes;
}