package com.delvin.loan.service;

import com.delvin.loan.dto.response.loanresp.CreditScoreResponse;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.Plafond;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Credit scoring, defined as the debt service ratio and nothing else.
 *
 * DSR = monthly instalment / monthly income * 100.
 *
 * The instalment is not stored anywhere, so it is derived here from the amount,
 * the tenor and the interest rate of the plafond the customer holds. That makes
 * this the one place the figure exists: a screen that recomputed it would
 * eventually disagree with the decision that was taken.
 *
 * **Only this application is counted.** A full DSR would add every instalment
 * the customer is already paying. That is deliberately out of scope here, and
 * it means the ratio understates the burden for a customer with a running loan.
 */
@Service
public class CreditScoreService {

    public static final String BAND_LOW = "LOW";
    public static final String BAND_MODERATE = "MODERATE";
    public static final String BAND_HIGH = "HIGH";
    public static final String BAND_VERY_HIGH = "VERY_HIGH";
    public static final String BAND_UNKNOWN = "UNKNOWN";

    /**
     * Where one band ends and the next begins, in percent. These are a lending
     * policy choice, not arithmetic - change them here and every screen follows.
     */
    public static final BigDecimal LOW_CEILING = new BigDecimal("30");
    public static final BigDecimal MODERATE_CEILING = new BigDecimal("40");
    public static final BigDecimal HIGH_CEILING = new BigDecimal("50");

    private static final int MONTHS_PER_YEAR = 12;
    private static final int DSR_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    public CreditScoreResponse evaluate(LoanApplication application) {

        if (application == null) {
            return unavailable(null, null, null, "No application");
        }

        BigDecimal income = application.getIncome();
        BigDecimal rate = rateOf(application);
        BigDecimal instalment = monthlyInstalment(
                application.getRequestedAmount(), application.getTenor(), rate);

        if (instalment == null) {
            String reason = rate == null
                    ? "The customer holds no plafond, so no interest rate applies"
                    : "The application has no amount or no tenor";
            return unavailable(null, rate, income, reason);
        }

        if (income == null || income.signum() <= 0) {
            return unavailable(instalment, rate, income,
                    "The application declares no monthly income");
        }

        BigDecimal dsr = instalment
                .divide(income, DSR_SCALE + 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(DSR_SCALE, RoundingMode.HALF_UP);

        return new CreditScoreResponse(instalment, rate, income, dsr, bandOf(dsr), null);
    }

    /**
     * The standard annuity instalment.
     *
     * Computed as principal * r * f / (f - 1) where f = (1 + r)^n, which is the
     * same figure as the textbook form with a negative exponent but avoids
     * raising a BigDecimal to a negative power.
     *
     * Returns null rather than zero when an input is missing: zero instalment
     * is a real answer at a zero rate, so it cannot double as "unknown".
     */
    public BigDecimal monthlyInstalment(BigDecimal principal, Integer tenor, BigDecimal annualRate) {

        if (principal == null || principal.signum() <= 0) return null;
        if (tenor == null || tenor <= 0) return null;
        if (annualRate == null || annualRate.signum() < 0) return null;

        if (annualRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(tenor), 0, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(MONTHS_PER_YEAR), MC);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(tenor, MC);

        return principal
                .multiply(monthlyRate, MC)
                .multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), 0, RoundingMode.HALF_UP);
    }

    public String bandOf(BigDecimal dsr) {
        if (dsr == null) return BAND_UNKNOWN;
        if (dsr.compareTo(LOW_CEILING) <= 0) return BAND_LOW;
        if (dsr.compareTo(MODERATE_CEILING) <= 0) return BAND_MODERATE;
        if (dsr.compareTo(HIGH_CEILING) <= 0) return BAND_HIGH;
        return BAND_VERY_HIGH;
    }

    private BigDecimal rateOf(LoanApplication application) {
        Customer customer = application.getCustomer();
        if (customer == null) return null;

        Plafond plafond = customer.getPlafond();
        return plafond == null ? null : plafond.getInterestRate();
    }

    private CreditScoreResponse unavailable(
            BigDecimal instalment, BigDecimal rate, BigDecimal income, String reason) {
        return new CreditScoreResponse(instalment, rate, income, null, BAND_UNKNOWN, reason);
    }
}
