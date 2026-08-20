package com.brika.platform.casemgmt.web;

import java.math.BigDecimal;

/** Sprint 27, Bloque 4: PATCH edits operation type plus optional amount/description. */
public record UpdateCaseApiRequest(
    String operationType, BigDecimal requestedAmount, String description) {

  /** Convenience for callers that update only the operation type. */
  public UpdateCaseApiRequest(String operationType) {
    this(operationType, null, null);
  }
}
