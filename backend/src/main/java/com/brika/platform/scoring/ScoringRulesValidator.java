package com.brika.platform.scoring;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * ADR-SCORING-001 D9-2/D9-3: validates categories and rules submitted to POST
 * /api/v1/scoring/rulesets against a closed schema, entirely at write time — an unrecognized field
 * or operator can never reach the engine, because it can never be persisted in the first place.
 * Deliberately independent of com.brika.platform.bankmatching.CriteriaRulesValidator (no
 * cross-domain coupling), though the discipline mirrors it exactly.
 */
@Component
public class ScoringRulesValidator {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");
  private static final int MAX_CATEGORY_NAME_LENGTH = 50;

  public List<CategoryThreshold> validateCategories(List<ScoringCategoryInput> categories) {
    if (categories == null || categories.isEmpty()) {
      throw invalidCategories("\"categories\" must be a non-empty array.");
    }

    List<CategoryThreshold> result = new ArrayList<>();
    Set<String> seenNames = new HashSet<>();
    int nullCount = 0;

    for (int i = 0; i < categories.size(); i++) {
      ScoringCategoryInput category = categories.get(i);
      String name = category.name();
      if (name == null || name.isBlank()) {
        throw invalidCategories("categories[" + i + "].name must be a non-blank string.");
      }
      if (name.length() > MAX_CATEGORY_NAME_LENGTH) {
        throw invalidCategories(
            "categories["
                + i
                + "].name must be at most "
                + MAX_CATEGORY_NAME_LENGTH
                + " characters.");
      }
      if (!seenNames.add(name)) {
        throw invalidCategories("Duplicate category name \"" + name + "\".");
      }

      BigDecimal maxScore = category.maxScore();
      if (maxScore == null) {
        nullCount++;
        if (i != categories.size() - 1) {
          throw invalidCategories("The category with maxScore=null must be the last element.");
        }
      } else {
        if (i > 0) {
          BigDecimal previous = categories.get(i - 1).maxScore();
          if (previous != null && maxScore.compareTo(previous) < 0) {
            throw invalidCategories("categories must be ordered ascending by maxScore.");
          }
        }
      }

      result.add(new CategoryThreshold(name, maxScore));
    }

    if (nullCount != 1) {
      throw invalidCategories(
          "Exactly one category with maxScore=null (catch-all) is required, found "
              + nullCount
              + ".");
    }

    return result;
  }

  public List<ScoringRuleDefinition> validateRules(List<ScoringRuleInput> rules) {
    if (rules == null || rules.isEmpty()) {
      throw invalidRules("\"rules\" must be a non-empty array (at least one rule is required).");
    }

    List<ScoringRuleDefinition> result = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();

    for (int i = 0; i < rules.size(); i++) {
      ScoringRuleDefinition rule = validateRule(rules.get(i), i);
      if (!seenCodes.add(rule.code())) {
        throw invalidRules(
            "Duplicate rule code \"" + rule.code() + "\" — codes must be unique within a ruleset.");
      }
      result.add(rule);
    }

    return result;
  }

  private ScoringRuleDefinition validateRule(ScoringRuleInput input, int index) {
    String code = input.code();
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw invalidRules("rules[" + index + "].code must match " + CODE_PATTERN.pattern() + ".");
    }

    if (input.weight() == null) {
      throw invalidRules("rules[" + index + "].weight is required.");
    }

    String fieldName = input.field();
    if (fieldName == null || !ScoreField.isKnown(fieldName)) {
      throw invalidRules(
          "rules[" + index + "].field \"" + fieldName + "\" is not a recognized field.");
    }
    ScoreField field = ScoreField.fromJsonName(fieldName).orElseThrow();

    ScoreOperator operator = parseOperator(input.operator(), index);
    ScoreValue value = validateValue(input.value(), operator, index);

    return new ScoringRuleDefinition(code, input.weight(), field, operator, value);
  }

  private ScoreValue validateValue(JsonNode valueNode, ScoreOperator operator, int index) {
    if (valueNode == null || valueNode.isNull()) {
      throw invalidRules("rules[" + index + "].value is required.");
    }

    if (operator.requiresArray()) {
      if (!valueNode.isArray()) {
        throw invalidRules(
            "rules[" + index + "].value must be an array for operator " + operator + ".");
      }
      List<BigDecimal> values = new ArrayList<>();
      for (JsonNode element : valueNode) {
        if (!element.isNumber()) {
          throw invalidRules("rules[" + index + "].value elements must all be numbers.");
        }
        values.add(element.decimalValue());
      }
      if (operator == ScoreOperator.BETWEEN) {
        if (values.size() != 2) {
          throw invalidRules(
              "rules[" + index + "].value must have exactly 2 elements [min, max] for BETWEEN.");
        }
        if (values.get(0).compareTo(values.get(1)) > 0) {
          throw invalidRules("rules[" + index + "].value min must be <= max for BETWEEN.");
        }
      } else if (values.isEmpty()) {
        throw invalidRules(
            "rules[" + index + "].value must have at least 1 element for " + operator + ".");
      }
      return ScoreValue.ofArray(values);
    }

    if (!valueNode.isNumber()) {
      throw invalidRules(
          "rules[" + index + "].value must be a number for operator " + operator + ".");
    }
    return ScoreValue.ofScalar(valueNode.decimalValue());
  }

  private ScoreOperator parseOperator(String name, int index) {
    try {
      return ScoreOperator.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw invalidRules(
          "rules[" + index + "].operator \"" + name + "\" is not a recognized operator.");
    }
  }

  private ValidationException invalidCategories(String message) {
    return new ValidationException("INVALID_SCORING_CATEGORIES", message);
  }

  private ValidationException invalidRules(String message) {
    return new ValidationException("INVALID_SCORING_RULES", message);
  }
}
