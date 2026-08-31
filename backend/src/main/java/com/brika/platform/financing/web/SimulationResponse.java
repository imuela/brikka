package com.brika.platform.financing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BRIKKA V2 I4. {@code interestRate} is the effective annual rate (= {@code finalInterestRate}; for
 * MIXED, the fixed-tranche final rate). {@code estimatedPayment} is the server-computed
 * French-amortization payment (for MIXED, the fixed tranche). {@code variablePhase} is present only
 * for MIXED — the re-amortized payment of the variable tranche after the fixed period.
 */
public record SimulationResponse(
    UUID id,
    UUID caseId,
    BigDecimal principal,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal estimatedPayment,
    String interestType,
    BigDecimal baseInterestRate,
    BigDecimal finalInterestRate,
    BigDecimal euriborRate,
    BigDecimal spreadRate,
    Integer fixedPeriodMonths,
    BigDecimal fixedPeriodRate,
    boolean icoGuarantee,
    List<Bonification> bonifications,
    VariablePhase variablePhase,
    Map<String, Object> metadata,
    UUID createdBy,
    Instant createdAt) {

  public record Bonification(String code, String label, BigDecimal rate, boolean active) {}

  public record VariablePhase(
      BigDecimal baseInterestRate,
      BigDecimal finalInterestRate,
      BigDecimal outstandingBalanceAtSwitch,
      BigDecimal monthlyPayment) {}
}
