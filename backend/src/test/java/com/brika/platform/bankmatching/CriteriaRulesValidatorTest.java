package com.brika.platform.bankmatching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** ADR-BANKENGINE-001 §2/§8: the closed JSON Schema, enforced at write time. */
class CriteriaRulesValidatorTest {

  private final CriteriaRulesValidator validator = new CriteriaRulesValidator(new ObjectMapper());

  private static final String VALID_RULES =
      """
      {"rules": [
        {"id": "max-ltv-80", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.80, "severity": "FAIL", "reason": "LTV must not exceed 80%"}
      ]}
      """;

  @Test
  void validRulesJsonParsesSuccessfully() {
    MatchingRuleSet ruleSet = validator.validate(VALID_RULES);
    assertThat(ruleSet.rules()).hasSize(1);
    MatchingRule rule = ruleSet.rules().get(0);
    assertThat(rule.id()).isEqualTo("max-ltv-80");
    assertThat(rule.field()).isEqualTo(MatchField.LTV);
    assertThat(rule.operator()).isEqualTo(MatchOperator.LESS_THAN_OR_EQUAL);
    assertThat(rule.severity()).isEqualTo(MatchSeverity.FAIL);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatFails("{not json", "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsNonObjectTopLevel() {
    assertThatFails("[]", "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsTopLevelWithExtraKey() {
    assertThatFails("{\"rules\": [], \"extra\": 1}", "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsEmptyRulesArray() {
    assertThatFails("{\"rules\": []}", "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsUnknownField() {
    String json = VALID_RULES.replace("computed.ltv", "client.income");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsUnknownOperator() {
    String json = VALID_RULES.replace("LESS_THAN_OR_EQUAL", "REGEX");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsInvalidSeverity() {
    String json = VALID_RULES.replace("\"FAIL\"", "\"ERROR\"");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsMissingKey() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.8, "severity": "FAIL"}
        ]}
        """;
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsExtraKey() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.8, "severity": "FAIL", "reason": "x", "extra": 1}
        ]}
        """;
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsBetweenWithWrongArraySize() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "financingRequest.termMonths", "operator": "BETWEEN", "value": [60], "severity": "WARNING", "reason": "x"}
        ]}
        """;
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsBetweenWithMinGreaterThanMax() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "financingRequest.termMonths", "operator": "BETWEEN", "value": [360, 60], "severity": "WARNING", "reason": "x"}
        ]}
        """;
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void acceptsBetweenWithValidRange() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "financingRequest.termMonths", "operator": "BETWEEN", "value": [60, 360], "severity": "WARNING", "reason": "x"}
        ]}
        """;
    MatchingRuleSet ruleSet = validator.validate(json);
    assertThat(ruleSet.rules()).hasSize(1);
  }

  @Test
  void rejectsDuplicateRuleId() {
    String json =
        """
        {"rules": [
          {"id": "r1", "field": "computed.ltv", "operator": "LESS_THAN_OR_EQUAL", "value": 0.8, "severity": "FAIL", "reason": "x"},
          {"id": "r1", "field": "financingRequest.termMonths", "operator": "GREATER_THAN", "value": 12, "severity": "WARNING", "reason": "y"}
        ]}
        """;
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsBlankReason() {
    String json = VALID_RULES.replace("\"LTV must not exceed 80%\"", "\"\"");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsInvalidRuleIdPattern() {
    String json = VALID_RULES.replace("max-ltv-80", "MaxLTV_80!");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  @Test
  void rejectsNonNumericValueForScalarOperator() {
    String json = VALID_RULES.replace("0.80", "\"eighty\"");
    assertThatFails(json, "INVALID_CRITERIA_RULES");
  }

  private void assertThatFails(String json, String expectedCode) {
    assertThatThrownBy(() -> validator.validate(json))
        .isInstanceOf(ValidationException.class)
        .satisfies(e -> assertThat(((ValidationException) e).code()).isEqualTo(expectedCode));
  }
}
