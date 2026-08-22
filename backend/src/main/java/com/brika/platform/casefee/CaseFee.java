package com.brika.platform.casefee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 32. Honorarios del broker para un caso — un registro "vigente" mutable por caso, con
 * historial append-only en {@link CaseFeeHistoryEntry} (mismo patrón que ClientFinancialProfile,
 * Sprint 30). Ver V25 para la justificación de dominio (pertenece al caso, no al cliente ni a la
 * empresa) y de la base de cálculo (siempre introducida explícitamente, nunca derivada de otra
 * tabla).
 */
public record CaseFee(
    UUID id,
    UUID companyId,
    UUID caseId,
    String feeType,
    BigDecimal fixedAmount,
    BigDecimal percentage,
    BigDecimal calculationBase,
    BigDecimal calculatedAmount,
    String status,
    Instant agreedAt,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt) {}
