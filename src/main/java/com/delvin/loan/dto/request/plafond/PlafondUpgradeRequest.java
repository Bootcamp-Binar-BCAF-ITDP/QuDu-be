package com.delvin.loan.dto.request.plafond;

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
public class PlafondUpgradeRequest {

    @NotNull(message = "requestedAmount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "requestedAmount must be greater than 0")
    private BigDecimal requestedAmount;
}