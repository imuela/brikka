package com.brika.platform.scoring;

import java.math.BigDecimal;

/** Result of evaluating a single ScoringRuleDefinition against a ScoreInputSnapshot. */
public record RuleEvaluation(
    String ruleCode,
    ScoreField field,
    ScoreOperator operator,
    ScoreValue value,
    BigDecimal evaluatedValue,
    RuleOutcome outcome,
    BigDecimal weight,
    BigDecimal contribution) {}
