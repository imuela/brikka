package com.brika.platform.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-SCORING-001 D9-2/D9-3: the closed schema for categories and rules, enforced at write time.
 */
class ScoringRulesValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ScoringRulesValidator validator = new ScoringRulesValidator();

  private JsonNode json(String literal) {
    try {
      return objectMapper.readTree(literal);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private List<ScoringCategoryInput> validCategories() {
    return List.of(
        new ScoringCategoryInput("LOW", new BigDecimal("40")),
        new ScoringCategoryInput("MEDIUM", new BigDecimal("70")),
        new ScoringCategoryInput("HIGH", null));
  }

  private ScoringRuleInput validRule() {
    return new ScoringRuleInput(
        "ltv-low", new BigDecimal("20"), "computed.ltv", "LESS_THAN_OR_EQUAL", json("0.6"));
  }

  // --- categories ---

  @Test
  void validCategoriesParseSuccessfully() {
    List<CategoryThreshold> result = validator.validateCategories(validCategories());
    assertThat(result).hasSize(3);
    assertThat(result.get(0).name()).isEqualTo("LOW");
    assertThat(result.get(2).maxScore()).isNull();
  }

  @Test
  void rejectsNullCategories() {
    assertThatThrownBy(() -> validator.validateCategories(null))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).code())
        .isEqualTo("INVALID_SCORING_CATEGORIES");
  }

  @Test
  void rejectsEmptyCategories() {
    assertThatThrownBy(() -> validator.validateCategories(List.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsBlankCategoryName() {
    assertThatThrownBy(
            () -> validator.validateCategories(List.of(new ScoringCategoryInput("  ", null))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsDuplicateCategoryNames() {
    assertThatThrownBy(
            () ->
                validator.validateCategories(
                    List.of(
                        new ScoringCategoryInput("LOW", new BigDecimal("40")),
                        new ScoringCategoryInput("LOW", null))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsZeroNullMaxScoreCategories() {
    assertThatThrownBy(
            () ->
                validator.validateCategories(
                    List.of(new ScoringCategoryInput("LOW", new BigDecimal("40")))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsMultipleNullMaxScoreCategories() {
    assertThatThrownBy(
            () ->
                validator.validateCategories(
                    List.of(
                        new ScoringCategoryInput("LOW", null),
                        new ScoringCategoryInput("HIGH", null))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNullMaxScoreNotLast() {
    assertThatThrownBy(
            () ->
                validator.validateCategories(
                    List.of(
                        new ScoringCategoryInput("LOW", null),
                        new ScoringCategoryInput("HIGH", new BigDecimal("70")))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsDescendingMaxScore() {
    assertThatThrownBy(
            () ->
                validator.validateCategories(
                    List.of(
                        new ScoringCategoryInput("HIGH", new BigDecimal("70")),
                        new ScoringCategoryInput("LOW", new BigDecimal("40")),
                        new ScoringCategoryInput("CATCHALL", null))))
        .isInstanceOf(ValidationException.class);
  }

  // --- rules ---

  @Test
  void validRuleParsesSuccessfully() {
    List<ScoringRuleDefinition> result = validator.validateRules(List.of(validRule()));
    assertThat(result).hasSize(1);
    ScoringRuleDefinition rule = result.get(0);
    assertThat(rule.code()).isEqualTo("ltv-low");
    assertThat(rule.field()).isEqualTo(ScoreField.LTV);
    assertThat(rule.operator()).isEqualTo(ScoreOperator.LESS_THAN_OR_EQUAL);
    assertThat(rule.value().asScalar()).isEqualByComparingTo("0.6");
  }

  @Test
  void rejectsNullOrEmptyRules() {
    assertThatThrownBy(() -> validator.validateRules(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> validator.validateRules(List.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsDuplicateRuleCodes() {
    assertThatThrownBy(() -> validator.validateRules(List.of(validRule(), validRule())))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsInvalidRuleCode() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "Bad Code!", new BigDecimal("10"), "computed.ltv", "EQUALS", json("1"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsMissingWeight() {
    ScoringRuleInput bad =
        new ScoringRuleInput("rule-1", null, "computed.ltv", "EQUALS", json("1"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsUnknownField() {
    ScoringRuleInput bad =
        new ScoringRuleInput("rule-1", new BigDecimal("10"), "client.income", "EQUALS", json("1"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).code())
        .isEqualTo("INVALID_SCORING_RULES");
  }

  @Test
  void rejectsUnknownOperator() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1", new BigDecimal("10"), "computed.ltv", "MATCHES_REGEX", json("1"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsScalarValueForArrayOperator() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1", new BigDecimal("10"), "financingRequest.termMonths", "BETWEEN", json("60"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsArrayValueForScalarOperator() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1", new BigDecimal("10"), "computed.ltv", "LESS_THAN_OR_EQUAL", json("[1,2]"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsBetweenWithWrongElementCount() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1",
            new BigDecimal("10"),
            "financingRequest.termMonths",
            "BETWEEN",
            json("[60,120,360]"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsBetweenWithMinGreaterThanMax() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1",
            new BigDecimal("10"),
            "financingRequest.termMonths",
            "BETWEEN",
            json("[360,60]"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsEmptyArrayForInOperator() {
    ScoringRuleInput bad =
        new ScoringRuleInput(
            "rule-1", new BigDecimal("10"), "financingRequest.termMonths", "IN", json("[]"));
    assertThatThrownBy(() -> validator.validateRules(List.of(bad)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void acceptsNegativeWeight() {
    ScoringRuleInput negative =
        new ScoringRuleInput(
            "high-debt", new BigDecimal("-10"), "computed.ltv", "GREATER_THAN", json("0.9"));
    List<ScoringRuleDefinition> result = validator.validateRules(List.of(negative));
    assertThat(result.get(0).weight()).isEqualByComparingTo("-10");
  }
}
