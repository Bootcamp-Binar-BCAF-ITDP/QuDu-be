package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class LoanDecisionResponse {
    private Integer decisionId;
    private String applicationId;
    private UserSummary branchManager;
    private String decision;
    private String decisionNote;
    private LocalDate decidedAt;
}
