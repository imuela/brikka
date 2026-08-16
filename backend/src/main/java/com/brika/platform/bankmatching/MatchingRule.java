package com.brika.platform.bankmatching;

/** A single, validated rule from bank_criteria_versions.rules (ADR-BANKENGINE-001 §2). */
public record MatchingRule(
    String id,
    MatchField field,
    MatchOperator operator,
    MatchValue value,
    MatchSeverity severity,
    String reason) {}
