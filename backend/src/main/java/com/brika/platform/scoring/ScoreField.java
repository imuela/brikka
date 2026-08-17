package com.brika.platform.scoring;

import java.util.Arrays;
import java.util.Optional;

/**
 * ADR-SCORING-001 D9-4/D9-5: the only 5 fields a scoring rule may reference. Deliberately
 * independent of com.brika.platform.bankmatching.MatchField — no cross-domain coupling.
 */
public enum ScoreField {
  TERM_MONTHS("financingRequest.termMonths"),
  REQUESTED_AMOUNT("financingRequest.requestedAmount"),
  VALUATION("property.valuation"),
  PURCHASE_PRICE("property.purchasePrice"),
  LTV("computed.ltv");

  private final String jsonName;

  ScoreField(String jsonName) {
    this.jsonName = jsonName;
  }

  public String jsonName() {
    return jsonName;
  }

  public static boolean isKnown(String jsonName) {
    return fromJsonName(jsonName).isPresent();
  }

  public static Optional<ScoreField> fromJsonName(String jsonName) {
    return Arrays.stream(values()).filter(f -> f.jsonName.equals(jsonName)).findFirst();
  }
}
