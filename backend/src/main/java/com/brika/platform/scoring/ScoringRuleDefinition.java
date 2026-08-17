package com.brika.platform.scoring;

import java.math.BigDecimal;

/** A single, validated scoring rule (ADR-SCORING-001 D9-2). weight may be negative. */
public record ScoringRuleDefinition(
    String code, BigDecimal weight, ScoreField field, ScoreOperator operator, ScoreValue value) {}
