package com.brika.platform.casemgmt.web;

import java.math.BigDecimal;

/**
 * operationType is free text: no catalog is documented anywhere (Sprint 3 pre-flight review).
 * Sprint 27, Bloque 4 adds optional requestedAmount/description (initial operation info, §7).
 */
public record CreateCaseApiRequest(
    String operationType, BigDecimal requestedAmount, String description) {

  /** Convenience for callers that create with only the operation type. */
  public CreateCaseApiRequest(String operationType) {
    this(operationType, null, null);
  }
}
