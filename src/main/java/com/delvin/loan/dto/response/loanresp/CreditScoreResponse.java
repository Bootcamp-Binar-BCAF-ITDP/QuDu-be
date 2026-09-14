package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreResponse {

    private BigDecimal monthlyInstalment;

    private BigDecimal annualInterestRate;

    private BigDecimal monthlyIncome;

    private BigDecimal dsr;

    private String band;

    private String unavailableReason;
}
