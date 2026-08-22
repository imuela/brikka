package com.brika.platform.casefee.web;

import com.brika.platform.casefee.CaseFee;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CaseFeeResponse(
    UUID id,
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
    Instant updatedAt) {

  public static CaseFeeResponse from(CaseFee fee) {
    return new CaseFeeResponse(
        fee.id(),
        fee.caseId(),
        fee.feeType(),
        fee.fixedAmount(),
        fee.percentage(),
        fee.calculationBase(),
        fee.calculatedAmount(),
        fee.status(),
        fee.agreedAt(),
        fee.updatedBy(),
        fee.createdAt(),
        fee.updatedAt());
  }
}
