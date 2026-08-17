package com.brika.platform.ai.web;

import com.brika.platform.ai.AiUseCaseService;
import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.scoring.ScoringResult;
import com.brika.platform.scoring.ScoringResultRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 21_AI_V1_SCOPE.md §2.C. No dedicated "explain" permission exists in the catalog, so the generic
 * AI_USE (D10-1) is the only reasonable technical mapping for this use case — a disclosed minor
 * technical choice, not a business decision. Access is derived: scoring result -> case, same 3-hop
 * pattern as AiDocumentExtractionController#get. A scoring result in another tenant resolves to a
 * case CaseAccessService cannot find in the caller's tenant, so it is masked as 404 exactly like
 * every other cross-tenant lookup in this codebase.
 */
@RestController
public class AiExplanationController {

  private final CaseAccessService caseAccessService;
  private final AiUseCaseService aiUseCaseService;
  private final ScoringResultRepository scoringResultRepository;
  private final AuditEventWriter auditEventWriter;

  public AiExplanationController(
      CaseAccessService caseAccessService,
      AiUseCaseService aiUseCaseService,
      ScoringResultRepository scoringResultRepository,
      AuditEventWriter auditEventWriter) {
    this.caseAccessService = caseAccessService;
    this.aiUseCaseService = aiUseCaseService;
    this.scoringResultRepository = scoringResultRepository;
    this.auditEventWriter = auditEventWriter;
  }

  @PostMapping("/api/v1/scoring-results/{id}/ai/explanation")
  public AiUseCaseResponse explain(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody AiUseCaseApiRequest request) {
    ScoringResult scoringResult =
        scoringResultRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("SCORING_RESULT_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "AI_USE", scoringResult.caseId());

    var result =
        aiUseCaseService.explain(
            access.tenantId(), access.theCase().id(), access.user().id(), request.context());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "AI_EXPLANATION_REQUESTED",
        "SCORING_RESULT",
        id,
        "{\"scoringResultId\":\"" + id + "\"}");
    return AiUseCaseResponse.from(result);
  }
}
