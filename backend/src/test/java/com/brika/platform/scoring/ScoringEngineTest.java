package com.brika.platform.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-SCORING-001: pure, deterministic evaluation — TRIGGERED/NOT_TRIGGERED/NOT_EVALUATED,
 * aggregation by simple sum, category resolution by ascending threshold.
 */
class ScoringEngineTest {

  private final ScoringEngine engine = new ScoringEngine();

  private ScoringRuleDefinition rule(
      String code, String weight, ScoreField field, ScoreOperator operator, String value) {
    return new ScoringRuleDefinition(
        code, new BigDecimal(weight), field, operator, ScoreValue.ofScalar(new BigDecimal(value)));
  }

  private static final ScoreInputSnapshot FULL_SNAPSHOT =
      new ScoreInputSnapshot(
          new BigDecimal("300"),
          new BigDecimal("190000"),
          new BigDecimal("260000"),
          new BigDecimal("250000"),
          new BigDecimal("0.76"));

  private static final ScoreInputSnapshot EMPTY_SNAPSHOT =
      new ScoreInputSnapshot(null, null, null, null, null);

  @Test
  void ruleTriggersWhenConditionIsMet() {
    ScoringRuleDefinition r =
        rule("ltv-ok", "20", ScoreField.LTV, ScoreOperator.LESS_THAN_OR_EQUAL, "0.8");
    ScoringEvaluation evaluation = engine.evaluate(List.of(r), FULL_SNAPSHOT);
    assertThat(evaluation.ruleEvaluations().get(0).outcome()).isEqualTo(RuleOutcome.TRIGGERED);
    assertThat(evaluation.ruleEvaluations().get(0).contribution()).isEqualByComparingTo("20");
    assertThat(evaluation.totalScore()).isEqualByComparingTo("20");
  }

  @Test
  void ruleDoesNotTriggerWhenConditionIsNotMet() {
    ScoringRuleDefinition r =
        rule("ltv-strict", "20", ScoreField.LTV, ScoreOperator.LESS_THAN, "0.5");
    ScoringEvaluation evaluation = engine.evaluate(List.of(r), FULL_SNAPSHOT);
    assertThat(evaluation.ruleEvaluations().get(0).outcome()).isEqualTo(RuleOutcome.NOT_TRIGGERED);
    assertThat(evaluation.ruleEvaluations().get(0).contribution()).isEqualByComparingTo("0");
    assertThat(evaluation.totalScore()).isEqualByComparingTo("0");
  }

  @Test
  void ruleIsNotEvaluatedWhenFieldIsMissing() {
    ScoringRuleDefinition r =
        rule("ltv-ok", "20", ScoreField.LTV, ScoreOperator.LESS_THAN_OR_EQUAL, "0.8");
    ScoringEvaluation evaluation = engine.evaluate(List.of(r), EMPTY_SNAPSHOT);
    assertThat(evaluation.ruleEvaluations().get(0).outcome()).isEqualTo(RuleOutcome.NOT_EVALUATED);
    assertThat(evaluation.ruleEvaluations().get(0).evaluatedValue()).isNull();
    assertThat(evaluation.ruleEvaluations().get(0).contribution()).isEqualByComparingTo("0");
    assertThat(evaluation.totalScore()).isEqualByComparingTo("0");
  }

  @Test
  void negativeWeightSubtractsFromTotalWhenTriggered() {
    ScoringRuleDefinition r =
        rule("high-ltv-penalty", "-10", ScoreField.LTV, ScoreOperator.GREATER_THAN, "0.7");
    ScoringEvaluation evaluation = engine.evaluate(List.of(r), FULL_SNAPSHOT);
    assertThat(evaluation.ruleEvaluations().get(0).outcome()).isEqualTo(RuleOutcome.TRIGGERED);
    assertThat(evaluation.totalScore()).isEqualByComparingTo("-10");
  }

  @Test
  void sumsContributionsOfMultipleRulesIncludingMixedOutcomes() {
    ScoringRuleDefinition triggered =
        rule("ltv-ok", "20", ScoreField.LTV, ScoreOperator.LESS_THAN_OR_EQUAL, "0.8");
    ScoringRuleDefinition notTriggered =
        rule("term-long", "15", ScoreField.TERM_MONTHS, ScoreOperator.GREATER_THAN, "400");
    ScoringRuleDefinition notEvaluated =
        new ScoringRuleDefinition(
            "amount-check",
            new BigDecimal("5"),
            ScoreField.REQUESTED_AMOUNT,
            ScoreOperator.GREATER_THAN,
            ScoreValue.ofScalar(new BigDecimal("500000")));
    ScoringEvaluation evaluation =
        engine.evaluate(
            List.of(triggered, notTriggered, notEvaluated),
            new ScoreInputSnapshot(
                new BigDecimal("300"),
                null,
                new BigDecimal("260000"),
                new BigDecimal("250000"),
                new BigDecimal("0.76")));
    assertThat(evaluation.totalScore()).isEqualByComparingTo("20");
  }

  @Test
  void categoryResolvesToLowerBoundaryCategory() {
    List<CategoryThreshold> categories =
        List.of(
            new CategoryThreshold("LOW", new BigDecimal("40")),
            new CategoryThreshold("MEDIUM", new BigDecimal("70")),
            new CategoryThreshold("HIGH", null));
    assertThat(engine.resolveCategory(categories, new BigDecimal("40"))).isEqualTo("LOW");
    assertThat(engine.resolveCategory(categories, new BigDecimal("41"))).isEqualTo("MEDIUM");
    assertThat(engine.resolveCategory(categories, new BigDecimal("70"))).isEqualTo("MEDIUM");
  }

  @Test
  void categoryResolvesToCatchAllAboveHighestThreshold() {
    List<CategoryThreshold> categories =
        List.of(
            new CategoryThreshold("LOW", new BigDecimal("40")),
            new CategoryThreshold("HIGH", null));
    assertThat(engine.resolveCategory(categories, new BigDecimal("1000"))).isEqualTo("HIGH");
  }

  @Test
  void categoryResolvesToCatchAllForNegativeTotal() {
    List<CategoryThreshold> categories =
        List.of(
            new CategoryThreshold("LOW", new BigDecimal("40")),
            new CategoryThreshold("HIGH", null));
    assertThat(engine.resolveCategory(categories, new BigDecimal("-10"))).isEqualTo("LOW");
  }
}
