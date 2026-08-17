package com.brika.platform.scoring;

/**
 * ADR-SCORING-001: the only 3 possible outcomes of evaluating a single scoring rule. There is no
 * severity concept in scoring (unlike bank matching's FAIL/WARNING) — a rule either contributes its
 * weight (TRIGGERED) or it doesn't (NOT_TRIGGERED / NOT_EVALUATED).
 */
public enum RuleOutcome {
  TRIGGERED,
  NOT_TRIGGERED,
  NOT_EVALUATED
}
