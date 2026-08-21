package com.brika.platform.financialanalysis;

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
}
