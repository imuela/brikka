package com.brika.platform.financing;

import java.math.BigDecimal;

/**
 * BRIKKA V2 I4. Deterministic output of {@link SimulationInterestCalculator}. The {@code
 * variablePhase*} fields are populated only for {@code MIXED} simulations (the payment of the
 * variable tranche after the fixed period), {@code null} otherwise.
 *
 * <ul>
 *   <li>{@code baseInterestRate} — rate before bonifications (FIXED: the fixed rate; VARIABLE:
 *       euribor + spread; MIXED: the fixed-tranche rate). Scale 4.
 *   <li>{@code finalInterestRate} — {@code max(0, base - Σ active bonifications)}. Scale 4. This is
 *       the effective rate persisted as {@code simulations.interest_rate} and used downstream.
 *   <li>{@code estimatedPayment} — French-amortization monthly payment at {@code finalInterestRate}
 *       over the full term (for MIXED this is the fixed-tranche payment). Scale 2.
 *   <li>{@code variablePhaseBaseRate} / {@code variablePhaseFinalRate} — MIXED only: euribor +
 *       spread, before / after the same bonifications. Scale 4.
 *   <li>{@code outstandingBalanceAtSwitch} — MIXED only: principal still owed when the fixed period
 *       ends. Scale 2.
 *   <li>{@code variablePhaseEstimatedPayment} — MIXED only: payment on the outstanding balance at
 *       {@code variablePhaseFinalRate} over the remaining months. Scale 2.
 * </ul>
 */
public record SimulationComputation(
    BigDecimal baseInterestRate,
    BigDecimal finalInterestRate,
    BigDecimal estimatedPayment,
    BigDecimal variablePhaseBaseRate,
    BigDecimal variablePhaseFinalRate,
    BigDecimal outstandingBalanceAtSwitch,
    BigDecimal variablePhaseEstimatedPayment) {}
