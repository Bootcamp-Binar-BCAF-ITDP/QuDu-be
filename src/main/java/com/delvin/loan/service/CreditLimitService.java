package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.Customer;
import com.delvin.loan.repository.LoanDisbursementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreditLimitService {

    private final LoanDisbursementRepository disbursementRepository;

    public BigDecimal grantedLimit(Customer customer) {

        if (customer.getApprovedLimit() != null) {
            return customer.getApprovedLimit();
        }

        return customer.getPlafond() != null ? customer.getPlafond().getMaxAmount() : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal usedLimit(String customerId) {

        BigDecimal used = disbursementRepository.sumDisbursedForCustomer(customerId, LoanStatus.DISBURSED);

        return used == null ? BigDecimal.ZERO : used;
    }

    @Transactional(readOnly = true)
    public BigDecimal availableLimit(Customer customer) {

        BigDecimal remaining = grantedLimit(customer).subtract(usedLimit(customer.getCustomerId()));

        return remaining.max(BigDecimal.ZERO);
    }
}
