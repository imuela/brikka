package com.brika.platform.casefee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 32. Append-only snapshot written on every write to CaseFee — same pattern as
 * ClientFinancialProfileHistoryEntry (Sprint 30).
 */
public record CaseFeeHistoryEntry(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID feeId,
    String feeType,
    BigDecimal fixedAmount,
    BigDecimal percentage,
    BigDecimal calculationBase,
    BigDecimal calculatedAmount,
    String status,
    Instant agreedAt,
    UUID changedBy,
    Instant changedAt) {}
