package com.brika.platform.bankmatching;

import java.math.BigDecimal;

/**
 * ADR-BANKENGINE-001 §1.3: the frozen input to a single matching execution. Any field may be {@code
 * null} — that is the sole trigger for NOT_EVALUATED (§4/§7), never an error.
 */
public record InputSnapshot(BigDecimal ltv, BigDecimal requestedAmount, BigDecimal termMonths) {

  public BigDecimal fieldValue(MatchField field) {
    return switch (field) {
      case LTV -> ltv;
      case REQUESTED_AMOUNT -> requestedAmount;
      case TERM_MONTHS -> termMonths;
    };
  }
}
