package com.brika.platform.financing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 15_DEFINITIVE_ERD.md: "No es una oferta bancaria." Purely an internal, non-binding estimate. */
public record Simulation(
    UUID id,
    UUID companyId,
    UUID caseId,
    BigDecimal principal,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal estimatedPayment,
    String metadata,
    UUID createdBy,
    Instant createdAt) {}
