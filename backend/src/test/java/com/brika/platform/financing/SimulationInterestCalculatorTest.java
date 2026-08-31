package com.brika.platform.financing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BRIKKA V2 I4. Interest breakdown + payment recomputation for FIXED / VARIABLE / MIXED. Payments
 * are cross-checked against {@link MortgagePaymentCalculator} directly (the calculator's own math
 * is verified in {@link MortgagePaymentCalculatorTest}); this class verifies the rate derivation,
 * the bonification application (R19 — really applied, unlike Legacy) and the MIXED two-tranche
 * handling.
 */
class SimulationInterestCalculatorTest {

  private static SimulationBonification bonification(String code, String rate, boolean active) {
    return new SimulationBonification(code, code, new BigDecimal(rate), active);
  }

  private static SimulationInterestModel fixed(
      String principal, int term, String fixedRate, List<SimulationBonification> bonifications) {
    return new SimulationInterestModel(
        SimulationInterestType.FIXED,
        new BigDecimal(principal),
        term,
        new BigDecimal(fixedRate),
        null,
        null,
        null,
        null,
        bonifications);
  }

  @Test
  void fixedWithoutBonificationsKeepsTheRateAndComputesTheFrenchPayment() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(fixed("200000", 300, "3.5", List.of()));

    assertThat(c.baseInterestRate()).isEqualByComparingTo("3.5000");
    assertThat(c.finalInterestRate()).isEqualByComparingTo("3.5000");
    assertThat(c.estimatedPayment())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("200000"), new BigDecimal("3.5000"), 300));
    assertThat(c.variablePhaseFinalRate()).isNull();
    assertThat(c.variablePhaseEstimatedPayment()).isNull();
  }

  @Test
  void fixedAppliesOnlyActiveBonificationsToTheRate() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(
            fixed(
                "200000",
                300,
                "3.5",
                List.of(
                    bonification("PAYROLL", "0.30", true),
                    bonification("HOME_INSURANCE", "0.15", true),
                    bonification("CARD", "0.10", false))));

    // 3.50 - (0.30 + 0.15) = 3.05 ; the inactive 0.10 is ignored
    assertThat(c.finalInterestRate()).isEqualByComparingTo("3.0500");
    assertThat(c.finalInterestRate()).isLessThanOrEqualTo(c.baseInterestRate());
  }

  @Test
  void bonificationsCannotDriveTheRateBelowZero() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(
            fixed("120000", 240, "0.20", List.of(bonification("PAYROLL", "0.50", true))));

    assertThat(c.finalInterestRate()).isEqualByComparingTo("0.0000");
    // rate 0 -> straight-line payment
    assertThat(c.estimatedPayment())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("120000"), BigDecimal.ZERO, 240));
  }

  @Test
  void variableRateIsEuriborPlusSpreadMinusActiveBonifications() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(
            new SimulationInterestModel(
                SimulationInterestType.VARIABLE,
                new BigDecimal("180000"),
                360,
                null,
                new BigDecimal("2.10"),
                new BigDecimal("0.99"),
                null,
                null,
                List.of(bonification("PAYROLL", "0.50", true))));

    assertThat(c.baseInterestRate()).isEqualByComparingTo("3.0900");
    assertThat(c.finalInterestRate()).isEqualByComparingTo("2.5900");
    assertThat(c.estimatedPayment())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("180000"), new BigDecimal("2.5900"), 360));
  }

  @Test
  void variableRateAcceptsANegativeEuribor() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(
            new SimulationInterestModel(
                SimulationInterestType.VARIABLE,
                new BigDecimal("150000"),
                240,
                null,
                new BigDecimal("-0.20"),
                new BigDecimal("0.90"),
                null,
                null,
                List.of()));

    assertThat(c.baseInterestRate()).isEqualByComparingTo("0.7000");
    assertThat(c.finalInterestRate()).isEqualByComparingTo("0.7000");
  }

  @Test
  void mixedComputesBothTranchesWithTheBonificationsAppliedToEach() {
    SimulationComputation c =
        SimulationInterestCalculator.compute(
            new SimulationInterestModel(
                SimulationInterestType.MIXED,
                new BigDecimal("220000"),
                360,
                null,
                new BigDecimal("2.00"),
                new BigDecimal("0.80"),
                120,
                new BigDecimal("2.80"),
                List.of(bonification("PAYROLL", "0.20", true))));

    // fixed tranche: 2.80 -> 2.60
    assertThat(c.baseInterestRate()).isEqualByComparingTo("2.8000");
    assertThat(c.finalInterestRate()).isEqualByComparingTo("2.6000");
    assertThat(c.estimatedPayment())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("220000"), new BigDecimal("2.6000"), 360));

    // variable tranche: 2.00 + 0.80 = 2.80 -> 2.60, re-amortized over the outstanding balance
    assertThat(c.variablePhaseBaseRate()).isEqualByComparingTo("2.8000");
    assertThat(c.variablePhaseFinalRate()).isEqualByComparingTo("2.6000");
    BigDecimal balance =
        MortgagePaymentCalculator.computeOutstandingBalance(
            new BigDecimal("220000"), new BigDecimal("2.6000"), 360, 120);
    assertThat(c.outstandingBalanceAtSwitch()).isEqualByComparingTo(balance);
    assertThat(c.variablePhaseEstimatedPayment())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                balance, new BigDecimal("2.6000"), 240));
  }

  @Test
  void sumOfActiveBonificationsIsWhatIsSubtracted() {
    SimulationComputation none =
        SimulationInterestCalculator.compute(fixed("100000", 240, "3.0", List.of()));
    SimulationComputation several =
        SimulationInterestCalculator.compute(
            fixed(
                "100000",
                240,
                "3.0",
                List.of(
                    bonification("A", "0.10", true),
                    bonification("B", "0.25", true),
                    bonification("C", "0.05", true),
                    bonification("D", "0.40", false))));

    assertThat(none.finalInterestRate()).isEqualByComparingTo("3.0000");
    // 3.00 - (0.10 + 0.25 + 0.05) = 2.60  (D inactive)
    assertThat(several.finalInterestRate()).isEqualByComparingTo("2.6000");
  }
}
