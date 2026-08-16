package com.brika.platform.bankmatching;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** ADR-BANKENGINE-001 §4/§5/§6: per-rule semantics and aggregation, pure and deterministic. */
class MatchingEngineTest {

  private final CriteriaRulesValidator validator = new CriteriaRulesValidator(new ObjectMapper());
  private final MatchingEngine engine = new MatchingEngine();

  private MatchingRuleSet ruleSet(String json) {
    return validator.validate(json);
  }

  @Test
  void passWhenConditionHolds() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "x"}]}
            """);
    InputSnapshot snapshot = new InputSnapshot(new BigDecimal("0.70"), null, null);

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.ruleEvaluations()).hasSize(1);
    assertThat(evaluation.ruleEvaluations().get(0).result()).isEqualTo(MatchResult.PASS);
    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.PASS);
  }

  @Test
  void failWhenSeverityFailAndConditionFalse() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "x"}]}
            """);
    InputSnapshot snapshot = new InputSnapshot(new BigDecimal("0.90"), null, null);

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.ruleEvaluations().get(0).result()).isEqualTo(MatchResult.FAIL);
    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.FAIL);
  }

  @Test
  void warningWhenSeverityWarningAndConditionFalse() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "financingRequest.termMonths", "operator": "GREATER_THAN_OR_EQUAL", "value": 60, "severity": "WARNING", "reason": "x"}]}
            """);
    InputSnapshot snapshot = new InputSnapshot(null, null, new BigDecimal("36"));

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.ruleEvaluations().get(0).result()).isEqualTo(MatchResult.WARNING);
    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.WARNING);
  }

  @Test
  void notEvaluatedWhenFieldIsNull() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "x"}]}
            """);
    InputSnapshot snapshot = new InputSnapshot(null, null, null); // no Property for the case

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.ruleEvaluations().get(0).result()).isEqualTo(MatchResult.NOT_EVALUATED);
    assertThat(evaluation.ruleEvaluations().get(0).evaluatedValue()).isNull();
    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.NOT_EVALUATED);
  }

  @Test
  void aggregationFailBeatsWarningBeatsPass() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [
              {"id": "pass-rule", "field": "financingRequest.termMonths", "operator": "GREATER_THAN", "value": 12, "severity": "FAIL", "reason": "x"},
              {"id": "warn-rule", "field": "financingRequest.requestedAmount", "operator": "LESS_THAN", "value": 100000, "severity": "WARNING", "reason": "y"},
              {"id": "fail-rule", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "z"}
            ]}
            """);
    InputSnapshot snapshot =
        new InputSnapshot(new BigDecimal("0.95"), new BigDecimal("200000"), new BigDecimal("300"));

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.FAIL);
  }

  @Test
  void aggregationAllNotEvaluatedGivesGlobalNotEvaluated() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [
              {"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "x"},
              {"id": "r2", "field": "computed.ltv", "operator": "GREATER_THAN", "value": 0.10, "severity": "WARNING", "reason": "y"}
            ]}
            """);
    InputSnapshot snapshot =
        new InputSnapshot(null, new BigDecimal("200000"), new BigDecimal("300"));

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.NOT_EVALUATED);
  }

  @Test
  void aggregationPassPlusNotEvaluatedGivesGlobalPass() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [
              {"id": "pass-rule", "field": "financingRequest.termMonths", "operator": "GREATER_THAN", "value": 12, "severity": "FAIL", "reason": "x"},
              {"id": "not-evaluated-rule", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "y"}
            ]}
            """);
    InputSnapshot snapshot = new InputSnapshot(null, null, new BigDecimal("300"));

    EngineEvaluation evaluation = engine.evaluate(rules, snapshot);

    assertThat(evaluation.globalResult()).isEqualTo(MatchResult.PASS);
  }

  @Test
  void betweenOperatorIsInclusiveOfBounds() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "financingRequest.termMonths", "operator": "BETWEEN", "value": [60, 360], "severity": "WARNING", "reason": "x"}]}
            """);

    EngineEvaluation atLowerBound =
        engine.evaluate(rules, new InputSnapshot(null, null, new BigDecimal("60")));
    EngineEvaluation atUpperBound =
        engine.evaluate(rules, new InputSnapshot(null, null, new BigDecimal("360")));
    EngineEvaluation outside =
        engine.evaluate(rules, new InputSnapshot(null, null, new BigDecimal("12")));

    assertThat(atLowerBound.globalResult()).isEqualTo(MatchResult.PASS);
    assertThat(atUpperBound.globalResult()).isEqualTo(MatchResult.PASS);
    assertThat(outside.globalResult()).isEqualTo(MatchResult.WARNING);
  }

  @Test
  void inOperatorMatchesAnyElement() {
    MatchingRuleSet rules =
        ruleSet(
            """
            {"rules": [{"id": "r1", "field": "financingRequest.termMonths", "operator": "IN", "value": [120, 240, 360], "severity": "WARNING", "reason": "x"}]}
            """);

    EngineEvaluation matching =
        engine.evaluate(rules, new InputSnapshot(null, null, new BigDecimal("240")));
    EngineEvaluation notMatching =
        engine.evaluate(rules, new InputSnapshot(null, null, new BigDecimal("180")));

    assertThat(matching.globalResult()).isEqualTo(MatchResult.PASS);
    assertThat(notMatching.globalResult()).isEqualTo(MatchResult.WARNING);
  }
}
