package com.delvin.loan.dto.request.plafond;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class PlafondRequest {

    @NotNull(message = "level is required")
    @Min(value = 1, message = "level must be at least 1")
    private Integer level;

    @NotBlank(message = "description is required")
    private String description;

    @NotNull(message = "minimumAmount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "minimumAmount must be greater than 0")
    private BigDecimal minimumAmount;

    @NotNull(message = "maxAmount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "maxAmount must be greater than 0")
    private BigDecimal maxAmount;

    @NotNull(message = "minTenor is required")
    @Min(value = 1, message = "minTenor must be at least 1 month")
    private Integer minTenor;

    @NotNull(message = "maxTenor is required")
    @Min(value = 1, message = "maxTenor must be at least 1 month")
    private Integer maxTenor;

    @NotNull(message = "interestRate is required")
    @DecimalMin(value = "0.0", message = "interestRate cannot be negative")
    private BigDecimal interestRate;

    @NotNull(message = "adminFee is required")
    @DecimalMin(value = "0.0", message = "adminFee cannot be negative")
    private BigDecimal adminFee;

    private Boolean isActive;
}