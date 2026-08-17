package com.brika.platform.scoring;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-SCORING-001 D9-11: evaluates every scoring_ruleset with status='ACTIVE' against a single
 * server-side snapshot of the case, persisting one immutable scoring_result per ruleset. Rules and
 * categories are re-parsed and re-validated at evaluation time (defense in depth, mirrors
 * BankMatchingService's practice) even though write-time validation already guarantees they are
 * well-formed.
 */
@Service
public class ScoringService {

  private final ScoringRulesetRepository scoringRulesetRepository;
  private final ScoringRuleRepository scoringRuleRepository;
  private final ScoringResultRepository scoringResultRepository;
  private final ScoreInputSnapshotFactory snapshotFactory;
  private final ScoringEngine scoringEngine;
  private final ScoringRulesValidator validator;
  private final ObjectMapper objectMapper;

  public ScoringService(
      ScoringRulesetRepository scoringRulesetRepository,
      ScoringRuleRepository scoringRuleRepository,
      ScoringResultRepository scoringResultRepository,
      ScoreInputSnapshotFactory snapshotFactory,
      ScoringEngine scoringEngine,
      ScoringRulesValidator validator,
      ObjectMapper objectMapper) {
    this.scoringRulesetRepository = scoringRulesetRepository;
    this.scoringRuleRepository = scoringRuleRepository;
    this.scoringResultRepository = scoringResultRepository;
    this.snapshotFactory = snapshotFactory;
    this.scoringEngine = scoringEngine;
    this.validator = validator;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public List<ScoringResult> run(UUID companyId, UUID caseId) {
    List<ScoringRuleset> activeRulesets = scoringRulesetRepository.findAllActive();
    if (activeRulesets.isEmpty()) {
      throw new ValidationException(
          "NO_ACTIVE_SCORING_RULESET", "No active scoring ruleset to evaluate.");
    }

    ScoreInputSnapshot snapshot = snapshotFactory.build(caseId);
    List<ScoringResult> results = new ArrayList<>();

    for (ScoringRuleset ruleset : activeRulesets) {
      List<ScoringRule> ruleRows = scoringRuleRepository.findAllByRulesetId(ruleset.id());
      List<ScoringRuleDefinition> rules = parseRules(ruleRows);
      List<CategoryThreshold> categories = parseCategories(ruleset.categoriesJson());

      ScoringEvaluation evaluation = scoringEngine.evaluate(rules, snapshot);
      String category = scoringEngine.resolveCategory(categories, evaluation.totalScore());
      String explanationJson = buildExplanation(snapshot, evaluation);

      UUID resultId =
          scoringResultRepository.insert(
              companyId, caseId, ruleset.id(), evaluation.totalScore(), category, explanationJson);
      results.add(scoringResultRepository.findById(resultId).orElseThrow());
    }

    return results;
  }

  private List<CategoryThreshold> parseCategories(String categoriesJson) {
    JsonNode root = readJson(categoriesJson);
    List<ScoringCategoryInput> inputs = new ArrayList<>();
    for (JsonNode categoryNode : root.get("categories")) {
      String name = categoryNode.get("name").asText();
      JsonNode maxScoreNode = categoryNode.get("maxScore");
      BigDecimal maxScore =
          (maxScoreNode == null || maxScoreNode.isNull()) ? null : maxScoreNode.decimalValue();
      inputs.add(new ScoringCategoryInput(name, maxScore));
    }
    return validator.validateCategories(inputs);
  }

  private List<ScoringRuleDefinition> parseRules(List<ScoringRule> rows) {
    List<ScoringRuleInput> inputs = new ArrayList<>();
    for (ScoringRule row : rows) {
      JsonNode config = readJson(row.configurationJson());
      inputs.add(
          new ScoringRuleInput(
              row.code(),
              row.weight(),
              config.get("field").asText(),
              config.get("operator").asText(),
              config.get("value")));
    }
    return validator.validateRules(inputs);
  }

  private String buildExplanation(ScoreInputSnapshot snapshot, ScoringEvaluation evaluation) {
    Map<String, Object> snapshotMap = new LinkedHashMap<>();
    snapshotMap.put("termMonths", snapshot.termMonths());
    snapshotMap.put("requestedAmount", snapshot.requestedAmount());
    snapshotMap.put("valuation", snapshot.valuation());
    snapshotMap.put("purchasePrice", snapshot.purchasePrice());
    snapshotMap.put("ltv", snapshot.ltv());

    List<Map<String, Object>> rules = new ArrayList<>();
    for (RuleEvaluation ruleEvaluation : evaluation.ruleEvaluations()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("ruleCode", ruleEvaluation.ruleCode());
      entry.put("field", ruleEvaluation.field().jsonName());
      entry.put("operator", ruleEvaluation.operator().name());
      entry.put(
          "value",
          ruleEvaluation.value().isArray()
              ? ruleEvaluation.value().asArray()
              : ruleEvaluation.value().asScalar());
      entry.put("evaluatedValue", ruleEvaluation.evaluatedValue());
      entry.put("outcome", ruleEvaluation.outcome().name());
      entry.put("weight", ruleEvaluation.weight());
      entry.put("contribution", ruleEvaluation.contribution());
      rules.add(entry);
    }

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("snapshot", snapshotMap);
    root.put("rules", rules);
    return toJson(root);
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse stored scoring content", e);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize scoring result content", e);
    }
  }
}
