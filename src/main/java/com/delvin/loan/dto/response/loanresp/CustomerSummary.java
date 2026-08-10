package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CustomerSummary {
    private String customerId;
    private String customerName;
    private String email;
    private String phoneNumber;
}
