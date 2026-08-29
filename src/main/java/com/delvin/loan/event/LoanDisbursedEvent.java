package com.delvin.loan.event;

import java.math.BigDecimal;

public record LoanDisbursedEvent(
        String applicationId,
        String customerName,
        String customerEmail,
        BigDecimal disbursedAmount,
        String bankName,
        String accountNumber
) {}