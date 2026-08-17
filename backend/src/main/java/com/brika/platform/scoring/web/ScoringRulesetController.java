package com.brika.platform.scoring.web;

import com.brika.platform.scoring.ScoringRule;
import com.brika.platform.scoring.ScoringRuleset;
import com.brika.platform.scoring.ScoringRulesetService;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-SCORING-001 D9-7: minimal authoring/consultation infrastructure for scoring_rulesets. GLOBAL
 * (no company_id, no requireTenant() — same pattern as BankController). Never decides business
 * content (weights/thresholds/rules) — only validates and persists whatever the caller submits.
 */
@RestController
public class ScoringRulesetController {

  private final AuthorizationService authorizationService;
  private final ScoringRulesetService scoringRulesetService;
  private final ObjectMapper objectMapper;

  public ScoringRulesetController(
      AuthorizationService authorizationService,
      ScoringRulesetService scoringRulesetService,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.scoringRulesetService = scoringRulesetService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/api/v1/scoring/rulesets")
  public ScoringRulesetResponse create(
      Authentication authentication, @RequestBody CreateScoringRulesetApiRequest request) {
    authorizationService.requirePermission(authentication, "SCORING_RULESET_MANAGE");
    ScoringRuleset ruleset =
        scoringRulesetService.create(
            request.code(), request.version(), request.categories(), request.rules());
    return toResponse(ruleset);
  }

  @GetMapping("/api/v1/scoring/rulesets")
  public List<ScoringRulesetResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "SCORING_RULESET_READ");
    return scoringRulesetService.findAll().stream().map(this::toResponse).toList();
  }

  private ScoringRulesetResponse toResponse(ScoringRuleset ruleset) {
    List<ScoringRuleResponse> rules =
        scoringRulesetService.rulesFor(ruleset.id()).stream().map(this::toResponse).toList();
    return new ScoringRulesetResponse(
        ruleset.id(),
        ruleset.code(),
        ruleset.version(),
        ruleset.status(),
        readJson(ruleset.categoriesJson()),
        rules,
        ruleset.createdAt());
  }

  private ScoringRuleResponse toResponse(ScoringRule rule) {
    JsonNode configuration = readJsonNode(rule.configurationJson());
    return new ScoringRuleResponse(
        rule.id(),
        rule.code(),
        rule.weight(),
        configuration.get("field").asText(),
        configuration.get("operator").asText(),
        objectMapper.convertValue(configuration.get("value"), Object.class));
  }

  private Object readJson(String json) {
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private JsonNode readJsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse stored scoring rule configuration", e);
    }
  }
}
