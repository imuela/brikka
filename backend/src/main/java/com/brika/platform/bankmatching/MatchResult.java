package com.brika.platform.bankmatching;

/**
 * ADR-BANKENGINE-001 §4/§8: PASS/FAIL/WARNING/NOT_EVALUATED are the only possible outcomes of
 * evaluating a single rule. ERROR exists only as a global-result outcome (§8) — a well-formed rule
 * evaluation never produces it, since invalid rules are rejected at write time (§8/D-G) and can
 * therefore never reach the evaluator in the first place.
 */
public enum MatchResult {
  PASS,
  FAIL,
  WARNING,
  NOT_EVALUATED,
  ERROR
}
