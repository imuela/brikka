package com.brika.platform.scoring;

import java.math.BigDecimal;

/**
 * ADR-SCORING-001: server-side snapshot of the 5 approved fields, all nullable. Never accepted from
 * the client — always built by ScoreInputSnapshotFactory.
 */
public record ScoreInputSnapshot(
    BigDecimal termMonths,
    BigDecimal requestedAmount,
    BigDecimal valuation,
    BigDecimal purchasePrice,
    BigDecimal ltv) {

  public BigDecimal fieldValue(ScoreField field) {
    return switch (field) {
      case TERM_MONTHS -> termMonths;
      case REQUESTED_AMOUNT -> requestedAmount;
      case VALUATION -> valuation;
      case PURCHASE_PRICE -> purchasePrice;
      case LTV -> ltv;
    };
  }
}
