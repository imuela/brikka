package com.brika.platform.financialanalysis.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.financialanalysis.FinancialAnalysisResult;
import com.brika.platform.financialanalysis.FinancialAnalysisService;
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
 * Sprint 31. Case-scoped, same TENANT + ROLE/PERMISSION + CASE ASSIGNMENT pattern as every other
 * case-scoped resource since Sprint 3 (via CaseAccessService), mirroring ScoringController exactly.
 * Never reachable from com.brika.platform.portal — no Portal controller imports this package, and
 * FINANCIAL_ANALYSIS_RUN/READ are never granted to CLIENT (V24) — same "controlled business
 * information, no approved reason to expose it to Portal yet" reasoning as Sprint 30's
 * ClientFinancialProfileController.
 */
@RestController
public class FinancialAnalysisController {

  private final CaseAccessService caseAccessService;
  private final FinancialAnalysisService financialAnalysisService;
  private final ObjectMapper objectMapper;

  public FinancialAnalysisController(
      CaseAccessService caseAccessService,
      FinancialAnalysisService financialAnalysisService,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.financialAnalysisService = financialAnalysisService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/api/v1/cases/{caseId}/financial-analysis")
  public List<FinancialAnalysisResultResponse> run(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "FINANCIAL_ANALYSIS_RUN", caseId);
    UUID actorUserId = access.user().id();
    return financialAnalysisService
        .run(access.tenantId(), access.theCase().id(), actorUserId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/cases/{caseId}/financial-analysis")
  public List<FinancialAnalysisResultResponse> results(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "FINANCIAL_ANALYSIS_READ", caseId);
    return financialAnalysisService.results(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  private FinancialAnalysisResultResponse toResponse(FinancialAnalysisResult result) {
    return FinancialAnalysisResultResponse.from(result, readJson(result.explanationJson()));
  }

  private Object readJson(String json) {
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
