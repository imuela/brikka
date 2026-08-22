package com.brika.platform.casefee.web;

import com.brika.platform.casefee.CaseFeeHistoryEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CaseFeeHistoryResponse(
    UUID id,
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
    Instant changedAt) {

  public static CaseFeeHistoryResponse from(CaseFeeHistoryEntry entry) {
    return new CaseFeeHistoryResponse(
        entry.id(),
        entry.caseId(),
        entry.feeId(),
        entry.feeType(),
        entry.fixedAmount(),
        entry.percentage(),
        entry.calculationBase(),
        entry.calculatedAmount(),
        entry.status(),
        entry.agreedAt(),
        entry.changedBy(),
        entry.changedAt());
  }
}
