package com.delvin.loan.event;

import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.CustomerPlafondRequest;

import java.math.BigDecimal;

public record PlafondDecisionEvent(
        String requestId,
        String customerId,
        String customerName,
        String customerEmail,
        PlafondRequestStatus status,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        Integer previousLevel,
        Integer grantedLevel,
        String notes
) {

    public boolean approved() {
        return status == PlafondRequestStatus.APPROVED;
    }

    public BigDecimal effectiveAmount() {
        return approved() ? approvedAmount : requestedAmount;
    }

    public static PlafondDecisionEvent of(CustomerPlafondRequest request) {

        Customer customer = request.getCustomer();

        return new PlafondDecisionEvent(
                request.getRequestId(),
                customer == null ? null : customer.getCustomerId(),
                customer == null ? null : customer.getCustomerName(),
                customer == null ? null : customer.getEmail(),
                request.getStatus(),
                request.getRequestedAmount(),
                request.getApprovedAmount(),
                request.getCurrentPlafond() == null ? null : request.getCurrentPlafond().getLevel(),
                request.getRequestedPlafond() == null ? null : request.getRequestedPlafond().getLevel(),
                request.getNotes()
        );
    }
}
