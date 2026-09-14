package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The debt service ratio for one application.
 *
 * Every input is returned alongside the result on purpose: a reviewer who
 * disagrees with the number can see which instalment and which income produced
 * it without opening another screen, and the frontend never has to recompute
 * anything to explain it.
 *
 * [dsr] is null whenever the ratio cannot be computed rather than zero. Zero
 * would read as "no burden at all", which is the opposite of "we do not know".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreResponse {

    /** Derived from the amount, the tenor and the plafond rate. Null if any is missing. */
    private BigDecimal monthlyInstalment;

    /**
     * The annual rate the instalment was computed at, as a fraction (0.12 is
     * 12 percent). Taken from the plafond the customer holds, so it differs per
     * tier. Returned so a screen can show the real rate instead of guessing one.
     */
    private BigDecimal annualInterestRate;

    /** As declared on the application. */
    private BigDecimal monthlyIncome;

    /** instalment / income * 100, two decimals. Null when it cannot be computed. */
    private BigDecimal dsr;

    /** LOW, MODERATE, HIGH, VERY_HIGH, or UNKNOWN. */
    private String band;

    /** Why the ratio is missing, when it is. Null on success. */
    private String unavailableReason;
}
