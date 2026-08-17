package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.util.List;

/** Full outcome of evaluating one ruleset: per-rule breakdown + aggregated total_score. */
public record ScoringEvaluation(List<RuleEvaluation> ruleEvaluations, BigDecimal totalScore) {}
