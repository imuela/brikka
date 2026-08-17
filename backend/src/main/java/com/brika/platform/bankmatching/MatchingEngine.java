package com.brika.platform.bankmatching;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ADR-BANKENGINE-001 §5/§6: pure, stateless, deterministic. Assumes {@code ruleSet} already passed
 * {@link CriteriaRulesValidator} (guaranteed by write-time validation, §8/D-G) — never throws for a
 * well-formed rule set. No network/DB access, no randomness, no clock reads: the same (ruleSet,
 * snapshot) pair always produces the same EngineEvaluation.
 */
@Component
public class MatchingEngine {

  public EngineEvaluation evaluate(MatchingRuleSet ruleSet, InputSnapshot snapshot) {
    List<RuleEvaluation> evaluations = new ArrayList<>();
    for (MatchingRule rule : ruleSet.rules()) {
      evaluations.add(evaluateRule(rule, snapshot));
    }
    List<MatchResult> results = evaluations.stream().map(RuleEvaluation::result).toList();
    return new EngineEvaluation(evaluations, aggregateResults(results));
  }

  private RuleEvaluation evaluateRule(MatchingRule rule, InputSnapshot snapshot) {
    BigDecimal fieldValue = snapshot.fieldValue(rule.field());

    if (fieldValue == null) {
      return new RuleEvaluation(
          rule.id(),
          rule.field(),
          rule.operator(),
          rule.value(),
          null,
          MatchResult.NOT_EVALUATED,
          rule.reason());
    }

    boolean matched = rule.operator().apply(fieldValue, rule.value());
    MatchResult result = matched ? MatchResult.PASS : toResult(rule.severity());
    return new RuleEvaluation(
        rule.id(), rule.field(), rule.operator(), rule.value(), fieldValue, result, rule.reason());
  }

  private MatchResult toResult(MatchSeverity severity) {
    return severity == MatchSeverity.FAIL ? MatchResult.FAIL : MatchResult.WARNING;
  }

  /**
   * ADR-BANKENGINE-001 §6 / ADR-BANKENGINE-002 §3: FAIL > WARNING > NOT_EVALUATED-total > PASS.
   * Public so BankMatchOverrideService can reuse the exact same aggregation rule to derive the
   * effective global result from overridden per-rule results — the algorithm must never be
   * duplicated, only the input (original vs. effective per-rule results) differs.
   */
  public MatchResult aggregateResults(List<MatchResult> results) {
    boolean anyFail = false;
    boolean anyWarning = false;
    boolean anyNotEvaluated = false;
    boolean anySignificant = false; // PASS, FAIL or WARNING — anything but NOT_EVALUATED

    for (MatchResult result : results) {
      switch (result) {
        case FAIL -> {
          anyFail = true;
          anySignificant = true;
        }
        case WARNING -> {
          anyWarning = true;
          anySignificant = true;
        }
        case NOT_EVALUATED -> anyNotEvaluated = true;
        case PASS -> anySignificant = true;
        case ERROR -> throw new IllegalStateException("A rule evaluation can never produce ERROR");
      }
    }

    if (anyFail) {
      return MatchResult.FAIL;
    }
    if (anyWarning) {
      return MatchResult.WARNING;
    }
    if (!anySignificant && anyNotEvaluated) {
      return MatchResult.NOT_EVALUATED;
    }
    return MatchResult.PASS;
  }
}
