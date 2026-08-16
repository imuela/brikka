package com.brika.platform.bankmatching;

/** ADR-BANKENGINE-001 §2/§4: determines the per-rule result when the condition is false. */
public enum MatchSeverity {
  FAIL,
  WARNING
}
