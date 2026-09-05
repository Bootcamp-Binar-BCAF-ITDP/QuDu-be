package com.delvin.loan.event;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;

import java.math.BigDecimal;

public record LoanStatusChangedEvent(
        String applicationId,
        String customerId,
        String customerName,
        String customerEmail,
        String status,
        String decisionNote,
        BigDecimal amount,
        String bankName,
        String accountNumber
) {

    public boolean approved() {
        return LoanStatus.DISBURSED.equals(status);
    }

    public static LoanStatusChangedEvent of(LoanApplication application,
                                            String decisionNote,
                                            BigDecimal amount) {

        Customer customer = application.getCustomer();

        return new LoanStatusChangedEvent(
                application.getApplicationId(),
                customer == null ? null : customer.getCustomerId(),
                customer == null ? null : customer.getCustomerName(),
                customer == null ? null : customer.getEmail(),
                application.getStatus(),
                decisionNote,
                amount,
                application.getBank(),
                application.getBankAccountNumber()
        );
    }
}
