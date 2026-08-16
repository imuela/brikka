package com.brika.platform.financing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 15_DEFINITIVE_ERD.md: "Puede estar relacionada con uno o varios procesos bancarios." */
public record FinancingRequest(
    UUID id,
    UUID companyId,
    UUID caseId,
    String status,
    BigDecimal requestedAmount,
    int termMonths,
    Instant createdAt,
    Instant updatedAt) {}
