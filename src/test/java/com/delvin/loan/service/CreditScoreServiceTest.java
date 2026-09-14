package com.delvin.loan.service;

import com.delvin.loan.dto.response.loanresp.CreditScoreResponse;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.Plafond;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreditScoreServiceTest {

    private final CreditScoreService service = new CreditScoreService();

    private LoanApplication application(long amount, int tenor, long income, String annualRate) {
        LoanApplication application = TestFixtures.application("CHECKING");
        application.setRequestedAmount(BigDecimal.valueOf(amount));
        application.setTenor(tenor);
        application.setIncome(BigDecimal.valueOf(income));

        Plafond plafond = application.getCustomer().getPlafond();
        plafond.setInterestRate(annualRate == null ? null : new BigDecimal(annualRate));
        return application;
    }

    @Test
    @DisplayName("at a zero rate the instalment is simply the amount split across the tenor")
    void zeroRateSplitsEvenly() {
        assertThat(service.monthlyInstalment(BigDecimal.valueOf(12_000_000), 12, BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    @DisplayName("12 percent a year over 12 months follows the annuity formula")
    void annuityInstalment() {
        BigDecimal instalment = service.monthlyInstalment(
                BigDecimal.valueOf(12_000_000), 12, new BigDecimal("0.12"));

        assertThat(instalment).isEqualByComparingTo(BigDecimal.valueOf(1_066_185));
    }

    @Test
    @DisplayName("an interest bearing loan always costs more per month than the flat split")
    void interestCostsMoreThanFlat() {
        BigDecimal flat = service.monthlyInstalment(
                BigDecimal.valueOf(24_000_000), 24, BigDecimal.ZERO);
        BigDecimal withInterest = service.monthlyInstalment(
                BigDecimal.valueOf(24_000_000), 24, new BigDecimal("0.18"));

        assertThat(withInterest).isGreaterThan(flat);
    }

    @Test
    @DisplayName("a longer tenor lowers the instalment on the same amount")
    void longerTenorLowersInstalment() {
        BigDecimal short12 = service.monthlyInstalment(
                BigDecimal.valueOf(24_000_000), 12, new BigDecimal("0.12"));
        BigDecimal long24 = service.monthlyInstalment(
                BigDecimal.valueOf(24_000_000), 24, new BigDecimal("0.12"));

        assertThat(long24).isLessThan(short12);
    }

    @Test
    @DisplayName("the total repaid exceeds the amount borrowed whenever a rate applies")
    void totalRepaidExceedsPrincipal() {
        int tenor = 18;
        BigDecimal instalment = service.monthlyInstalment(
                BigDecimal.valueOf(30_000_000), tenor, new BigDecimal("0.15"));

        assertThat(instalment.multiply(BigDecimal.valueOf(tenor)))
                .isGreaterThan(BigDecimal.valueOf(30_000_000));
    }

    @Test
    @DisplayName("a missing amount, tenor or rate yields no instalment rather than zero")
    void missingInputsYieldNoInstalment() {
        assertThat(service.monthlyInstalment(null, 12, BigDecimal.ZERO)).isNull();
        assertThat(service.monthlyInstalment(BigDecimal.ZERO, 12, BigDecimal.ZERO)).isNull();
        assertThat(service.monthlyInstalment(BigDecimal.valueOf(-1), 12, BigDecimal.ZERO)).isNull();
        assertThat(service.monthlyInstalment(BigDecimal.valueOf(1_000_000), null, BigDecimal.ZERO)).isNull();
        assertThat(service.monthlyInstalment(BigDecimal.valueOf(1_000_000), 0, BigDecimal.ZERO)).isNull();
        assertThat(service.monthlyInstalment(BigDecimal.valueOf(1_000_000), 12, null)).isNull();
    }

    @Test
    @DisplayName("the ratio is the instalment over the income as a percentage")
    void ratioIsInstalmentOverIncome() {
        CreditScoreResponse score = service.evaluate(application(12_000_000, 12, 5_000_000, "0"));

        assertThat(score.getMonthlyInstalment()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(score.getMonthlyIncome()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(score.getDsr()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(score.getUnavailableReason()).isNull();
    }

    @Test
    @DisplayName("the rate reported back is the one the customer's plafond carries, not a fixed guess")
    void reportsTheTierRate() {
        CreditScoreResponse gold = service.evaluate(application(20_000_000, 12, 9_000_000, "0.18"));
        CreditScoreResponse bronze = service.evaluate(application(20_000_000, 12, 9_000_000, "0.09"));

        assertThat(gold.getAnnualInterestRate()).isEqualByComparingTo(new BigDecimal("0.18"));
        assertThat(bronze.getAnnualInterestRate()).isEqualByComparingTo(new BigDecimal("0.09"));
    }

    @Test
    @DisplayName("two customers on different tiers get different instalments for the same loan")
    void tierChangesTheInstalment() {
        CreditScoreResponse gold = service.evaluate(application(20_000_000, 12, 9_000_000, "0.18"));
        CreditScoreResponse bronze = service.evaluate(application(20_000_000, 12, 9_000_000, "0.09"));

        assertThat(gold.getMonthlyInstalment()).isGreaterThan(bronze.getMonthlyInstalment());
        assertThat(gold.getDsr()).isGreaterThan(bronze.getDsr());
    }

    @Test
    @DisplayName("the ratio is rounded to two decimals")
    void ratioIsRoundedToTwoDecimals() {
        CreditScoreResponse score = service.evaluate(application(10_000_000, 3, 7_000_000, "0"));

        assertThat(score.getDsr().scale()).isEqualTo(2);
        assertThat(score.getDsr()).isEqualByComparingTo(new BigDecimal("47.62"));
    }

    @ParameterizedTest(name = "a ratio of {0} percent is {1}")
    @CsvSource({
            "0.00,LOW",
            "29.99,LOW",
            "30.00,LOW",
            "30.01,MODERATE",
            "39.99,MODERATE",
            "40.00,MODERATE",
            "40.01,HIGH",
            "50.00,HIGH",
            "50.01,VERY_HIGH",
            "180.00,VERY_HIGH",
    })
    @DisplayName("each band boundary is inclusive at the top")
    void bandBoundaries(String dsr, String expected) {
        assertThat(service.bandOf(new BigDecimal(dsr))).isEqualTo(expected);
    }

    @Test
    @DisplayName("an income that cannot carry the instalment is flagged, not hidden")
    void unaffordableApplicationIsFlagged() {
        CreditScoreResponse score = service.evaluate(application(12_000_000, 6, 1_500_000, "0"));

        assertThat(score.getDsr()).isGreaterThan(new BigDecimal("100"));
        assertThat(score.getBand()).isEqualTo(CreditScoreService.BAND_VERY_HIGH);
    }

    @Test
    @DisplayName("no declared income yields no ratio, and says so")
    void missingIncomeYieldsNoRatio() {
        CreditScoreResponse score = service.evaluate(application(12_000_000, 12, 0, "0"));

        assertThat(score.getDsr()).isNull();
        assertThat(score.getBand()).isEqualTo(CreditScoreService.BAND_UNKNOWN);
        assertThat(score.getUnavailableReason()).contains("no monthly income");
        assertThat(score.getMonthlyInstalment()).isNotNull();
    }

    @Test
    @DisplayName("a null income yields no ratio")
    void nullIncomeYieldsNoRatio() {
        LoanApplication app = application(12_000_000, 12, 1, "0");
        app.setIncome(null);

        CreditScoreResponse score = service.evaluate(app);

        assertThat(score.getDsr()).isNull();
        assertThat(score.getBand()).isEqualTo(CreditScoreService.BAND_UNKNOWN);
    }

    @Test
    @DisplayName("a customer with no plafond yields no ratio, because no rate applies")
    void missingPlafondYieldsNoRatio() {
        LoanApplication app = application(12_000_000, 12, 5_000_000, "0.12");
        app.getCustomer().setPlafond(null);

        CreditScoreResponse score = service.evaluate(app);

        assertThat(score.getDsr()).isNull();
        assertThat(score.getMonthlyInstalment()).isNull();
        assertThat(score.getUnavailableReason()).contains("no plafond");
    }

    @Test
    @DisplayName("an application with no customer yields no ratio")
    void missingCustomerYieldsNoRatio() {
        LoanApplication app = application(12_000_000, 12, 5_000_000, "0.12");
        app.setCustomer(null);

        CreditScoreResponse score = service.evaluate(app);

        assertThat(score.getDsr()).isNull();
        assertThat(score.getBand()).isEqualTo(CreditScoreService.BAND_UNKNOWN);
    }

    @Test
    @DisplayName("an application with no tenor yields no ratio, and blames the tenor not the rate")
    void missingTenorYieldsNoRatio() {
        LoanApplication app = application(12_000_000, 12, 5_000_000, "0.12");
        app.setTenor(null);

        CreditScoreResponse score = service.evaluate(app);

        assertThat(score.getDsr()).isNull();
        assertThat(score.getUnavailableReason()).contains("no amount or no tenor");
    }

    @Test
    @DisplayName("a null application is handled rather than thrown on")
    void nullApplicationIsHandled() {
        CreditScoreResponse score = service.evaluate(null);

        assertThat(score.getDsr()).isNull();
        assertThat(score.getBand()).isEqualTo(CreditScoreService.BAND_UNKNOWN);
    }

    @Test
    @DisplayName("the income reported back is the one the ratio was computed from")
    void reportsTheInputsItUsed() {
        CreditScoreResponse score = service.evaluate(application(24_000_000, 24, 9_000_000, "0.12"));

        BigDecimal expected = score.getMonthlyInstalment()
                .divide(score.getMonthlyIncome(), 6, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertThat(score.getDsr()).isEqualByComparingTo(expected);
    }
}
