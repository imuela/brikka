package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ADR-SCORING-001: pure, stateless, deterministic. Assumes {@code rules} already passed
 * ScoringRulesValidator (guaranteed by write-time validation) — no network/DB access, no
 * randomness, no clock reads. The same (rules, snapshot) pair always produces the same
 * ScoringEvaluation.
 */
@Component
public class ScoringEngine {

  public ScoringEvaluation evaluate(
      List<ScoringRuleDefinition> rules, ScoreInputSnapshot snapshot) {
    List<RuleEvaluation> evaluations = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;

    for (ScoringRuleDefinition rule : rules) {
      RuleEvaluation evaluation = evaluateRule(rule, snapshot);
      evaluations.add(evaluation);
      total = total.add(evaluation.contribution());
    }

    return new ScoringEvaluation(evaluations, total);
  }

  private RuleEvaluation evaluateRule(ScoringRuleDefinition rule, ScoreInputSnapshot snapshot) {
    BigDecimal fieldValue = snapshot.fieldValue(rule.field());

    if (fieldValue == null) {
      return new RuleEvaluation(
          rule.code(),
          rule.field(),
          rule.operator(),
          rule.value(),
          null,
          RuleOutcome.NOT_EVALUATED,
          rule.weight(),
          BigDecimal.ZERO);
    }

    boolean triggered = rule.operator().apply(fieldValue, rule.value());
    RuleOutcome outcome = triggered ? RuleOutcome.TRIGGERED : RuleOutcome.NOT_TRIGGERED;
    BigDecimal contribution = triggered ? rule.weight() : BigDecimal.ZERO;

    return new RuleEvaluation(
        rule.code(),
        rule.field(),
        rule.operator(),
        rule.value(),
        fieldValue,
        outcome,
        rule.weight(),
        contribution);
  }

  /**
   * ADR-SCORING-001 D9-3: first category (ascending by maxScore) whose maxScore >= totalScore; the
   * final entry (maxScore == null) is the catch-all. Categories are guaranteed sorted/validated by
   * ScoringRulesValidator at write time.
   */
  public String resolveCategory(List<CategoryThreshold> categories, BigDecimal totalScore) {
    for (CategoryThreshold category : categories) {
      if (category.maxScore() == null || totalScore.compareTo(category.maxScore()) <= 0) {
        return category.name();
      }
    }
    throw new IllegalStateException(
        "No category matched — categories must end with a null maxScore catch-all");
  }
}
