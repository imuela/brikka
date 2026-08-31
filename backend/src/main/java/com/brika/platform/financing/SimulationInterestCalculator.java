package com.brika.platform.financing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * BRIKKA V2 I4. Pure, stateless, deterministic. Derives the base and final interest rates and the
 * recomputed monthly payment(s) of a simulation from its {@link SimulationInterestModel}. It is
 * <b>not</b> a second payment engine — every monthly payment goes through the existing {@link
 * MortgagePaymentCalculator} (French amortization), and the MIXED variable-tranche payment also
 * uses that class's {@code computeOutstandingBalance}.
 *
 * <p><b>Bonifications (R19, done right — Legacy stored them but never applied them):</b> {@code Σ}
 * = sum of {@code rate} over the entries whose {@code active} is true; {@code finalRate = max(0,
 * baseRate - Σ)}. The same {@code Σ} applies to both tranches of a MIXED simulation (a bonification
 * such as payroll or insurance is a condition on the mortgage as a whole, in force in either
 * tranche).
 *
 * <p><b>Rounding</b> (consistent with the rest of Brikka — {@code numeric(7,4)} rates, {@code
 * numeric(14,2)} money): the bonification sum is exact {@link BigDecimal} arithmetic; each rate
 * (base, final) is rounded once to scale 4 HALF_UP; every payment and the outstanding balance are
 * rounded by {@link MortgagePaymentCalculator} once to scale 2 HALF_UP. {@code double} is never
 * used.
 *
 * <p><b>MIXED scope:</b> the fixed tranche's payment is the standard full-term French-amortization
 * payment at the fixed final rate; the variable tranche's payment re-amortizes the real outstanding
 * balance at the modelled variable final rate over the remaining months. The variable rate is a
 * projection from the Euribor value entered at simulation time — a real future Euribor is
 * unknowable, and no attempt is made to model its path. {@code simulations.interest_rate} (the
 * value downstream services such as the financial analysis consume) is the fixed-tranche final
 * rate, i.e. the rate actually in effect at the start of the loan.
 */
public final class SimulationInterestCalculator {

  private static final int RATE_SCALE = 4;

  private SimulationInterestCalculator() {}

  public static SimulationComputation compute(SimulationInterestModel model) {
    BigDecimal totalBonification = sumActiveBonifications(model.bonifications());

    return switch (model.type()) {
      case FIXED -> {
        BigDecimal base = scaleRate(model.fixedRate());
        BigDecimal finalRate = applyBonifications(base, totalBonification);
        yield new SimulationComputation(
            base,
            finalRate,
            MortgagePaymentCalculator.computeMonthlyPayment(
                model.principal(), finalRate, model.termMonths()),
            null,
            null,
            null,
            null);
      }
      case VARIABLE -> {
        BigDecimal base = scaleRate(model.euriborRate().add(model.spreadRate()));
        BigDecimal finalRate = applyBonifications(base, totalBonification);
        yield new SimulationComputation(
            base,
            finalRate,
            MortgagePaymentCalculator.computeMonthlyPayment(
                model.principal(), finalRate, model.termMonths()),
            null,
            null,
            null,
            null);
      }
      case MIXED -> {
        BigDecimal fixedBase = scaleRate(model.fixedPeriodRate());
        BigDecimal fixedFinal = applyBonifications(fixedBase, totalBonification);
        BigDecimal fixedPayment =
            MortgagePaymentCalculator.computeMonthlyPayment(
                model.principal(), fixedFinal, model.termMonths());

        BigDecimal variableBase = scaleRate(model.euriborRate().add(model.spreadRate()));
        BigDecimal variableFinal = applyBonifications(variableBase, totalBonification);
        int fixedPeriodMonths = model.fixedPeriodMonths();
        BigDecimal balanceAtSwitch =
            MortgagePaymentCalculator.computeOutstandingBalance(
                model.principal(), fixedFinal, model.termMonths(), fixedPeriodMonths);
        BigDecimal variablePayment =
            MortgagePaymentCalculator.computeMonthlyPayment(
                balanceAtSwitch, variableFinal, model.termMonths() - fixedPeriodMonths);

        yield new SimulationComputation(
            fixedBase,
            fixedFinal,
            fixedPayment,
            variableBase,
            variableFinal,
            balanceAtSwitch,
            variablePayment);
      }
    };
  }

  private static BigDecimal sumActiveBonifications(List<SimulationBonification> bonifications) {
    BigDecimal sum = BigDecimal.ZERO;
    if (bonifications != null) {
      for (SimulationBonification bonification : bonifications) {
        if (bonification.active() && bonification.rate() != null) {
          sum = sum.add(bonification.rate());
        }
      }
    }
    return sum;
  }

  private static BigDecimal applyBonifications(BigDecimal baseRate, BigDecimal totalBonification) {
    BigDecimal reduced = baseRate.subtract(totalBonification);
    if (reduced.signum() < 0) {
      reduced = BigDecimal.ZERO;
    }
    return reduced.setScale(RATE_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleRate(BigDecimal rate) {
    return rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
  }
}
