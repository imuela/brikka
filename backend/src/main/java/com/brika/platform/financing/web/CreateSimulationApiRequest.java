package com.brika.platform.financing.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BRIKKA V2 I4. Enriched simulation input. {@code interestType} decides which rate fields are
 * required (FIXED: {@code fixedRate}; VARIABLE: {@code euriborRate} + {@code spreadRate}; MIXED:
 * {@code fixedPeriodMonths} + {@code fixedPeriodRate} + {@code euriborRate} + {@code spreadRate}).
 * The effective rate and the monthly payment are computed server-side — they are not accepted from
 * the client.
 */
public record CreateSimulationApiRequest(
    String interestType,
    BigDecimal principal,
    Integer termMonths,
    BigDecimal fixedRate,
    BigDecimal euriborRate,
    BigDecimal spreadRate,
    Integer fixedPeriodMonths,
    BigDecimal fixedPeriodRate,
    List<BonificationInput> bonifications,
    Boolean icoGuarantee,
    Map<String, Object> metadata) {

  public record BonificationInput(String code, String label, BigDecimal rate, Boolean active) {}
}
