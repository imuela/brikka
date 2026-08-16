package com.brika.platform.casemgmt.web;

import com.brika.platform.casemgmt.Case;
import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
    UUID id,
    UUID companyId,
    String reference,
    String status,
    String operationType,
    UUID createdBy,
    Instant createdAt,
    Instant cancelledAt) {

  public static CaseResponse from(Case theCase) {
    return new CaseResponse(
        theCase.id(),
        theCase.companyId(),
        theCase.reference(),
        theCase.status().name(),
        theCase.operationType(),
        theCase.createdBy(),
        theCase.createdAt(),
        theCase.cancelledAt());
  }
}
