package com.brika.platform.bankmatching;

import java.util.List;

/** Full outcome of one matching execution: per-rule results + the aggregated global result. */
public record EngineEvaluation(List<RuleEvaluation> ruleEvaluations, MatchResult globalResult) {}
