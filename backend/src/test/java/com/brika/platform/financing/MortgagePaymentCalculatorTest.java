package com.brika.platform.financing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MortgagePaymentCalculatorTest {

  @Test
  void normalInterestRateProducesTheKnownReferencePayment() {
    // Cross-checked independently with Python's Decimal (30 significant digits) — see Sprint 31
    // report. 200,000 EUR, 3.5% annual, 360 months (30 years).
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            new BigDecimal("200000"), new BigDecimal("3.5"), 360);
    assertThat(payment).isEqualByComparingTo("898.09");
  }

  @Test
  void secondReferenceCaseWithARoundOneMonthlyRate() {
    // 10,000 EUR, 12% annual (exactly 1%/month), 12 months.
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            new BigDecimal("10000"), new BigDecimal("12"), 12);
    assertThat(payment).isEqualByComparingTo("888.49");
  }

  @Test
  void zeroInterestRateIsAStraightLineDivision() {
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            new BigDecimal("12000"), BigDecimal.ZERO, 12);
    assertThat(payment).isEqualByComparingTo("1000.00");
  }

  @Test
  void zeroInterestRateWithARemainderStillRoundsHalfUpAtTwoDecimals() {
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(new BigDecimal("1000"), BigDecimal.ZERO, 3);
    // 1000/3 = 333.333... -> HALF_UP at scale 2 -> 333.33
    assertThat(payment).isEqualByComparingTo("333.33");
  }

  @Test
  void resultIsAlwaysScaleTwo() {
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            new BigDecimal("150000"), new BigDecimal("2.75"), 240);
    assertThat(payment.scale()).isEqualTo(2);
  }

  @Test
  void singleMonthTermRepaysPrincipalPlusOneMonthOfInterest() {
    // n=1: payment = principal * rate * (1+rate) / rate = principal * (1+rate)
    BigDecimal payment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            new BigDecimal("1000"), new BigDecimal("12"), 1);
    // monthlyRate = 1% -> payment = 1000 * 1.01 = 1010.00
    assertThat(payment).isEqualByComparingTo("1010.00");
  }

  // --- BRIKKA V2 I4: outstanding balance (MIXED variable tranche) ---

  @Test
  void outstandingBalanceBeforeAnyPaymentIsTheFullPrincipal() {
    assertThat(
            MortgagePaymentCalculator.computeOutstandingBalance(
                new BigDecimal("200000"), new BigDecimal("3.0"), 360, 0))
        .isEqualByComparingTo("200000.00");
  }

  @Test
  void outstandingBalanceAtOrBeyondTermIsZero() {
    assertThat(
            MortgagePaymentCalculator.computeOutstandingBalance(
                new BigDecimal("200000"), new BigDecimal("3.0"), 360, 360))
        .isEqualByComparingTo("0.00");
    assertThat(
            MortgagePaymentCalculator.computeOutstandingBalance(
                new BigDecimal("200000"), new BigDecimal("3.0"), 360, 999))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void zeroInterestBalanceIsStraightLine() {
    // 12000 over 12 months at 0% -> 1000/month. After 4 payments: 12000 - 4000 = 8000.
    assertThat(
            MortgagePaymentCalculator.computeOutstandingBalance(
                new BigDecimal("12000"), BigDecimal.ZERO, 12, 4))
        .isEqualByComparingTo("8000.00");
  }

  @Test
  void balanceDecreasesMonotonicallyAndStaysBelowPrincipal() {
    BigDecimal principal = new BigDecimal("220000");
    BigDecimal after60 =
        MortgagePaymentCalculator.computeOutstandingBalance(
            principal, new BigDecimal("2.6"), 360, 60);
    BigDecimal after120 =
        MortgagePaymentCalculator.computeOutstandingBalance(
            principal, new BigDecimal("2.6"), 360, 120);

    assertThat(after60).isLessThan(principal);
    assertThat(after120).isLessThan(after60);
    assertThat(after120.signum()).isPositive();
    assertThat(after120.scale()).isEqualTo(2);
  }

  @Test
  void balanceAfterKPaymentsReamortizesToTheSameOriginalPayment() {
    // Sanity: re-amortizing the outstanding balance at the SAME rate over the remaining term
    // reproduces the original monthly payment (to the cent).
    BigDecimal principal = new BigDecimal("180000");
    BigDecimal rate = new BigDecimal("3.25");
    BigDecimal original = MortgagePaymentCalculator.computeMonthlyPayment(principal, rate, 300);
    BigDecimal balance =
        MortgagePaymentCalculator.computeOutstandingBalance(principal, rate, 300, 100);
    BigDecimal reamortized = MortgagePaymentCalculator.computeMonthlyPayment(balance, rate, 200);

    assertThat(reamortized.subtract(original).abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
  }
}
