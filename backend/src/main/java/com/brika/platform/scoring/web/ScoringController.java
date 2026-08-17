package com.brika.platform.scoring.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.scoring.ScoringResult;
import com.brika.platform.scoring.ScoringResultRepository;
import com.brika.platform.scoring.ScoringService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-SCORING-001 §11 (D9-1 through D9-6): pre-computed, case-scoped scoring. TENANT +
 * ROLE/PERMISSION + CASE ASSIGNMENT via CaseAccessService, exactly as every other case-scoped
 * resource since Sprint 3. The snapshot is always built server-side — never accepted in the request
 * body.
 */
@RestController
public class ScoringController {

  private final CaseAccessService caseAccessService;
  private final ScoringService scoringService;
  private final ScoringResultRepository scoringResultRepository;
  private final ObjectMapper objectMapper;

  public ScoringController(
      CaseAccessService caseAccessService,
      ScoringService scoringService,
      ScoringResultRepository scoringResultRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.scoringService = scoringService;
    this.scoringResultRepository = scoringResultRepository;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/api/v1/cases/{caseId}/scoring/run")
  public List<ScoringResultResponse> run(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "SCORING_RUN", caseId);
    return scoringService.run(access.tenantId(), access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/cases/{caseId}/scoring/results")
  public List<ScoringResultResponse> results(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "SCORING_READ", caseId);
    return scoringResultRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  private ScoringResultResponse toResponse(ScoringResult result) {
    return new ScoringResultResponse(
        result.id(),
        result.caseId(),
        result.rulesetId(),
        result.totalScore(),
        result.category(),
        readJson(result.explanationJson()),
        result.calculatedAt());
  }

  private Object readJson(String json) {
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
