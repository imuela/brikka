package com.brika.platform.bankmatching;

/**
 * ADR-BANKENGINE-001 §1/D-F: closed set of evaluable inputs for Sprint 6B. No other field exists —
 * every other criterion category named in 06_BANK_ENGINE_SPECIFICATION.md §9 has no data source in
 * the schema and is explicitly out of scope (D-B/D-F).
 */
public enum MatchField {
  LTV("computed.ltv"),
  REQUESTED_AMOUNT("financingRequest.requestedAmount"),
  TERM_MONTHS("financingRequest.termMonths");

  private final String jsonName;

  MatchField(String jsonName) {
    this.jsonName = jsonName;
  }

  public String jsonName() {
    return jsonName;
  }

  public static MatchField fromJsonName(String jsonName) {
    for (MatchField field : values()) {
      if (field.jsonName.equals(jsonName)) {
        return field;
      }
    }
    throw new IllegalArgumentException("Unknown field: " + jsonName);
  }

  public static boolean isKnown(String jsonName) {
    for (MatchField field : values()) {
      if (field.jsonName.equals(jsonName)) {
        return true;
      }
    }
    return false;
  }
}
