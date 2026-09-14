package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.Customer;
import com.delvin.loan.repository.LoanDisbursementRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditLimitServiceTest {

    @Mock
    private LoanDisbursementRepository disbursementRepository;

    @InjectMocks
    private CreditLimitService service;

    @Test
    @DisplayName("grantedLimit uses approvedLimit when the branch manager has set one")
    void grantedLimitPrefersApprovedLimit() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(BigDecimal.valueOf(25_000_000));

        assertThat(service.grantedLimit(customer))
                .isEqualByComparingTo(BigDecimal.valueOf(25_000_000));
    }

    @Test
    @DisplayName("grantedLimit falls back to the plafond ceiling when no limit was approved")
    void grantedLimitFallsBackToPlafondCeiling() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(null);
        customer.setPlafond(TestFixtures.plafond(2, 5_000_000L, 30_000_000L));

        assertThat(service.grantedLimit(customer))
                .isEqualByComparingTo(BigDecimal.valueOf(30_000_000));
    }

    @Test
    @DisplayName("grantedLimit is zero when the customer has neither a limit nor a plafond")
    void grantedLimitZeroWithoutLimitOrPlafond() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(null);
        customer.setPlafond(null);

        assertThat(service.grantedLimit(customer)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("usedLimit sums only disbursed loans")
    void usedLimitSumsDisbursedLoans() {
        when(disbursementRepository.sumDisbursedForCustomer(
                TestFixtures.CUSTOMER_ID, LoanStatus.DISBURSED))
                .thenReturn(BigDecimal.valueOf(4_000_000));

        assertThat(service.usedLimit(TestFixtures.CUSTOMER_ID))
                .isEqualByComparingTo(BigDecimal.valueOf(4_000_000));
    }

    @Test
    @DisplayName("usedLimit reads a null sum as zero rather than propagating it")
    void usedLimitNullSumBecomesZero() {
        when(disbursementRepository.sumDisbursedForCustomer(any(), any()))
                .thenReturn(null);

        assertThat(service.usedLimit(TestFixtures.CUSTOMER_ID))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("availableLimit is the granted ceiling minus what has been drawn")
    void availableLimitSubtractsDrawnAmount() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(BigDecimal.valueOf(50_000_000));

        when(disbursementRepository.sumDisbursedForCustomer(
                TestFixtures.CUSTOMER_ID, LoanStatus.DISBURSED))
                .thenReturn(BigDecimal.valueOf(20_000_000));

        assertThat(service.availableLimit(customer))
                .isEqualByComparingTo(BigDecimal.valueOf(30_000_000));
    }

    @Test
    @DisplayName("availableLimit never goes negative when drawn exceeds the ceiling")
    void availableLimitFloorsAtZero() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(BigDecimal.valueOf(10_000_000));

        when(disbursementRepository.sumDisbursedForCustomer(any(), any()))
                .thenReturn(BigDecimal.valueOf(15_000_000));

        assertThat(service.availableLimit(customer))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("availableLimit equals the whole ceiling when nothing has been drawn")
    void availableLimitFullCeilingWhenNothingDrawn() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(BigDecimal.valueOf(10_000_000));

        when(disbursementRepository.sumDisbursedForCustomer(any(), any()))
                .thenReturn(null);

        assertThat(service.availableLimit(customer))
                .isEqualByComparingTo(BigDecimal.valueOf(10_000_000));
    }

    @Test
    @DisplayName("availableLimit uses the plafond ceiling when no limit was approved")
    void availableLimitUsesPlafondCeilingWhenUnapproved() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(null);
        customer.setPlafond(TestFixtures.plafond(3, 10_000_000L, 40_000_000L));

        when(disbursementRepository.sumDisbursedForCustomer(any(), any()))
                .thenReturn(BigDecimal.valueOf(5_000_000));

        assertThat(service.availableLimit(customer))
                .isEqualByComparingTo(BigDecimal.valueOf(35_000_000));
    }
}
