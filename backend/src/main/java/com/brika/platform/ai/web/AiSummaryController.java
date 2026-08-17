package com.brika.platform.ai.web;

import com.brika.platform.ai.AiUseCaseService;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 21_AI_V1_SCOPE.md §2.B. Synchronous use case, no Worker involvement (D10-3). Reuses
 * CaseAccessService (TENANT + ROLE/PERMISSION + CASE ASSIGNMENT), gated by AI_SUMMARIZE (D10-1).
 */
@RestController
public class AiSummaryController {

  private final CaseAccessService caseAccessService;
  private final AiUseCaseService aiUseCaseService;

  public AiSummaryController(
      CaseAccessService caseAccessService, AiUseCaseService aiUseCaseService) {
    this.caseAccessService = caseAccessService;
    this.aiUseCaseService = aiUseCaseService;
  }

  @PostMapping("/api/v1/cases/{caseId}/ai/summary")
  public AiUseCaseResponse summarize(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody AiUseCaseApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "AI_SUMMARIZE", caseId);
    return AiUseCaseResponse.from(
        aiUseCaseService.summarize(
            access.tenantId(), access.theCase().id(), access.user().id(), request.context()));
  }
}
