package com.brika.platform.financing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 15_DEFINITIVE_ERD.md: "No es una oferta bancaria." Purely an internal, non-binding estimate.
 *
 * <p>BRIKKA V2 I4: enriched with the interest structure (R18) and the applied bonifications (R19).
 * {@code interestRate} is kept as the <b>effective</b> annual rate that downstream consumers
 * (financial analysis, dossier) read — it equals {@code finalInterestRate} (for MIXED, the
 * fixed-tranche final rate). {@code estimatedPayment} is now server-computed via {@link
 * MortgagePaymentCalculator} (for MIXED, the fixed-tranche payment). {@code euriborRate}, {@code
 * spreadRate}, {@code fixedPeriodMonths}, {@code fixedPeriodRate} are {@code null} unless the type
 * needs them. {@code bonifications} is the JSON text of an array of {@link SimulationBonification}.
 */
public record Simulation(
    UUID id,
    UUID companyId,
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
    String bonifications,
    String metadata,
    UUID createdBy,
    Instant createdAt) {}
