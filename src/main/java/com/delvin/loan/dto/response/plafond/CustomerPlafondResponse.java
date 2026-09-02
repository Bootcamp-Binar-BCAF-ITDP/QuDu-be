package com.delvin.loan.dto.response.plafond;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPlafondResponse {

    private String customerId;
    private String customerName;

    private BigDecimal approvedLimit;

    private PlafondResponse plafond;
}