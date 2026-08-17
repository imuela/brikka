package com.brika.platform.scoring;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-SCORING-001 D9-7: creates a scoring_ruleset with its scoring_rules atomically. Never invents
 * business content (weights/thresholds/rules) — only validates the shape submitted by the caller
 * (SUPERADMIN) against the closed schema.
 */
@Service
public class ScoringRulesetService {

  private final ScoringRulesValidator validator;
  private final ScoringRulesetRepository scoringRulesetRepository;
  private final ScoringRuleRepository scoringRuleRepository;
  private final ObjectMapper objectMapper;

  public ScoringRulesetService(
      ScoringRulesValidator validator,
      ScoringRulesetRepository scoringRulesetRepository,
      ScoringRuleRepository scoringRuleRepository,
      ObjectMapper objectMapper) {
    this.validator = validator;
    this.scoringRulesetRepository = scoringRulesetRepository;
    this.scoringRuleRepository = scoringRuleRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ScoringRuleset create(
      String code,
      String version,
      List<ScoringCategoryInput> categoriesInput,
      List<ScoringRuleInput> rulesInput) {
    if (code == null || code.isBlank()) {
      throw new ValidationException("CODE_REQUIRED", "code is required.");
    }
    if (version == null || version.isBlank()) {
      throw new ValidationException("VERSION_REQUIRED", "version is required.");
    }

    List<CategoryThreshold> categories = validator.validateCategories(categoriesInput);
    List<ScoringRuleDefinition> rules = validator.validateRules(rulesInput);

    String categoriesJson = toJson(Map.of("categories", categories));
    UUID rulesetId = scoringRulesetRepository.insert(code, version, "ACTIVE", categoriesJson);

    for (ScoringRuleDefinition rule : rules) {
      scoringRuleRepository.insert(rulesetId, rule.code(), rule.weight(), toJson(configOf(rule)));
    }

    return scoringRulesetRepository.findById(rulesetId).orElseThrow();
  }

  public List<ScoringRuleset> findAll() {
    return scoringRulesetRepository.findAll();
  }

  public List<ScoringRule> rulesFor(UUID rulesetId) {
    return scoringRuleRepository.findAllByRulesetId(rulesetId);
  }

  private Map<String, Object> configOf(ScoringRuleDefinition rule) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("field", rule.field().jsonName());
    config.put("operator", rule.operator().name());
    config.put("value", rule.value().isArray() ? rule.value().asArray() : rule.value().asScalar());
    return config;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize scoring ruleset content", e);
    }
  }
}
