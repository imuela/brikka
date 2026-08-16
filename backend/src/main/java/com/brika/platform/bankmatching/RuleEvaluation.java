package com.brika.platform.bankmatching;

import java.math.BigDecimal;

/** Result of evaluating a single MatchingRule against an InputSnapshot (ADR-BANKENGINE-001 §5). */
public record RuleEvaluation(
    String ruleId,
    MatchField field,
    MatchOperator operator,
    MatchValue expectedValue,
    BigDecimal evaluatedValue,
    MatchResult result,
    String reason) {}
