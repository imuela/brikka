package com.brika.platform.financialanalysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Sprint 31. Centralized French-amortization (fixed monthly payment, "sistema francés") monthly
 * mortgage payment calculator — the single source of truth for this formula in Brikka. Neither
 * {@code Simulation.estimatedPayment} nor {@code BankOffer.payment} are computed values (both are
 * broker/bank-declared inputs, confirmed by inspecting their write paths before this sprint), so
 * this is genuinely the first real calculator in the codebase, not a duplicate of an existing one —
 * every caller that needs a computed payment should go through this class rather than re-deriving
 * the formula locally.
 *
 * <p><b>Formula</b>: monthlyRate = annualRatePercent / 100 / 12. If monthlyRate is zero, the
 * payment is a straight-line division of principal by termMonths (the standard French-amortization
 * formula has a removable singularity at rate=0; this is its limit, not a special case bolted on).
 * Otherwise: payment = principal &times; monthlyRate &times; (1+monthlyRate)^n / ((1+monthlyRate)^n
 * - 1), n = termMonths.
 *
 * <p><b>Precision</b>: intermediate arithmetic uses {@link MathContext#DECIMAL64} (16 significant
 * digits) to avoid compounding rounding error across the growth-factor exponentiation before the
 * final division; the returned payment is rounded only once, at the end, to scale 2 with {@link
 * RoundingMode#HALF_UP} — the same money convention already used throughout this codebase
 * (16_POSTGRESQL_SCHEMA_SPECIFICATION.md §2, "Dinero: numeric(14,2)"). {@code double} is never
 * used.
 */
public final class MortgagePaymentCalculator {

  private static final MathContext WORKING_PRECISION = MathContext.DECIMAL64;
  private static final int MONEY_SCALE = 2;
  private static final int MONTHS_PER_YEAR = 12;
  private static final int PERCENT_DIVISOR = 100;

  private MortgagePaymentCalculator() {}

  /**
   * @param principal financed capital, must be positive (validated by the caller)
   * @param annualInterestRatePercent nominal annual rate as a percentage (e.g. 3.5 for 3.5%), must
   *     be zero or positive (validated by the caller)
   * @param termMonths loan term in months, must be positive (validated by the caller)
   * @return the fixed monthly payment, scale 2, HALF_UP
   */
  public static BigDecimal computeMonthlyPayment(
      BigDecimal principal, BigDecimal annualInterestRatePercent, int termMonths) {
    BigDecimal monthlyRate =
        annualInterestRatePercent.divide(
            BigDecimal.valueOf(PERCENT_DIVISOR * MONTHS_PER_YEAR), WORKING_PRECISION);

    if (monthlyRate.signum() == 0) {
      return principal.divide(BigDecimal.valueOf(termMonths), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal growthFactor = BigDecimal.ONE.add(monthlyRate).pow(termMonths, WORKING_PRECISION);
    BigDecimal numerator =
        principal
            .multiply(monthlyRate, WORKING_PRECISION)
            .multiply(growthFactor, WORKING_PRECISION);
    BigDecimal denominator = growthFactor.subtract(BigDecimal.ONE, WORKING_PRECISION);
    return numerator.divide(denominator, MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
