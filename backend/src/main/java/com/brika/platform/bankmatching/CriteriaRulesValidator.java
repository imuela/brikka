package com.brika.platform.bankmatching;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * ADR-BANKENGINE-001 §2/§8/D-G: validates bank_criteria_versions.rules against the closed JSON
 * Schema at write time (BankController create/update criteria), so that a rule the evaluator cannot
 * understand can never be persisted in the first place — "unknown rule at evaluation time" is
 * structurally unreachable, not defended against at runtime.
 */
@Component
public class CriteriaRulesValidator {

  private static final Pattern RULE_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");
  private static final Set<String> RULE_KEYS =
      Set.of("id", "field", "operator", "value", "severity", "reason");

  private final ObjectMapper objectMapper;

  public CriteriaRulesValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public MatchingRuleSet validate(String rawJson) {
    JsonNode root = parse(rawJson);

    if (!root.isObject() || root.size() != 1 || !root.has("rules")) {
      throw invalid("Top-level payload must be a single-key object: {\"rules\": [...]}.");
    }

    JsonNode rulesNode = root.get("rules");
    if (!rulesNode.isArray() || rulesNode.isEmpty()) {
      throw invalid("\"rules\" must be a non-empty array (at least one rule is required).");
    }

    List<MatchingRule> rules = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();
    for (int i = 0; i < rulesNode.size(); i++) {
      MatchingRule rule = validateRule(rulesNode.get(i), i);
      if (!seenIds.add(rule.id())) {
        throw invalid(
            "Duplicate rule id \"" + rule.id() + "\" — rule ids must be unique within a version.");
      }
      rules.add(rule);
    }

    return new MatchingRuleSet(rules);
  }

  private MatchingRule validateRule(JsonNode node, int index) {
    if (!node.isObject()) {
      throw invalid(
          "Rule[" + index + "] must be an object with exactly the keys " + RULE_KEYS + ".");
    }
    for (String key : RULE_KEYS) {
      if (!node.has(key)) {
        throw invalid("Rule[" + index + "] is missing required key \"" + key + "\".");
      }
    }
    for (java.util.Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      if (!RULE_KEYS.contains(key)) {
        throw invalid("Rule[" + index + "] has unexpected key \"" + key + "\".");
      }
    }
    if (node.size() != RULE_KEYS.size()) {
      throw invalid("Rule[" + index + "] must have exactly the keys " + RULE_KEYS + ".");
    }

    String id = requireText(node.get("id"), index, "id");
    if (!RULE_ID_PATTERN.matcher(id).matches()) {
      throw invalid(
          "Rule[" + index + "].id \"" + id + "\" must match " + RULE_ID_PATTERN.pattern() + ".");
    }

    String fieldName = requireText(node.get("field"), index, "field");
    if (!MatchField.isKnown(fieldName)) {
      throw invalid("Rule[" + index + "].field \"" + fieldName + "\" is not a recognized field.");
    }
    MatchField field = MatchField.fromJsonName(fieldName);

    String operatorName = requireText(node.get("operator"), index, "operator");
    MatchOperator operator = parseOperator(operatorName, index);

    String severityName = requireText(node.get("severity"), index, "severity");
    MatchSeverity severity = parseSeverity(severityName, index);

    String reason = requireText(node.get("reason"), index, "reason");
    if (reason.isBlank() || reason.length() > 500) {
      throw invalid("Rule[" + index + "].reason must be non-blank and at most 500 characters.");
    }

    MatchValue value = validateValue(node.get("value"), operator, index);

    return new MatchingRule(id, field, operator, value, severity, reason);
  }

  private MatchValue validateValue(JsonNode valueNode, MatchOperator operator, int index) {
    if (operator.requiresArray()) {
      if (!valueNode.isArray()) {
        throw invalid("Rule[" + index + "].value must be an array for operator " + operator + ".");
      }
      List<BigDecimal> values = new ArrayList<>();
      for (JsonNode element : valueNode) {
        if (!element.isNumber()) {
          throw invalid("Rule[" + index + "].value elements must all be numbers.");
        }
        values.add(element.decimalValue());
      }
      if (operator == MatchOperator.BETWEEN) {
        if (values.size() != 2) {
          throw invalid(
              "Rule[" + index + "].value must have exactly 2 elements [min, max] for BETWEEN.");
        }
        if (values.get(0).compareTo(values.get(1)) > 0) {
          throw invalid("Rule[" + index + "].value min must be <= max for BETWEEN.");
        }
      } else if (values.isEmpty()) {
        throw invalid(
            "Rule[" + index + "].value must have at least 1 element for " + operator + ".");
      }
      return MatchValue.ofArray(values);
    }

    if (!valueNode.isNumber()) {
      throw invalid("Rule[" + index + "].value must be a number for operator " + operator + ".");
    }
    return MatchValue.ofScalar(valueNode.decimalValue());
  }

  private MatchOperator parseOperator(String name, int index) {
    try {
      return MatchOperator.valueOf(name);
    } catch (IllegalArgumentException e) {
      throw invalid("Rule[" + index + "].operator \"" + name + "\" is not a recognized operator.");
    }
  }

  private MatchSeverity parseSeverity(String name, int index) {
    try {
      return MatchSeverity.valueOf(name);
    } catch (IllegalArgumentException e) {
      throw invalid("Rule[" + index + "].severity \"" + name + "\" must be FAIL or WARNING.");
    }
  }

  private String requireText(JsonNode node, int index, String key) {
    if (node == null || !node.isTextual()) {
      throw invalid("Rule[" + index + "]." + key + " must be a string.");
    }
    return node.asText();
  }

  private JsonNode parse(String rawJson) {
    try {
      return objectMapper.readTree(rawJson);
    } catch (JsonProcessingException e) {
      throw invalid("Malformed JSON.");
    }
  }

  private ValidationException invalid(String message) {
    return new ValidationException("INVALID_CRITERIA_RULES", message);
  }
}
