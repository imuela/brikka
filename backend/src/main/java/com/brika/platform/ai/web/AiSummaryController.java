package com.brika.platform.ai.web;

import com.brika.platform.ai.AiUseCaseService;
import com.brika.platform.audit.AuditEventWriter;
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
  private final AuditEventWriter auditEventWriter;

  public AiSummaryController(
      CaseAccessService caseAccessService,
      AiUseCaseService aiUseCaseService,
      AuditEventWriter auditEventWriter) {
    this.caseAccessService = caseAccessService;
    this.aiUseCaseService = aiUseCaseService;
    this.auditEventWriter = auditEventWriter;
  }

  @PostMapping("/api/v1/cases/{caseId}/ai/summary")
  public AiUseCaseResponse summarize(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody AiUseCaseApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "AI_SUMMARIZE", caseId);
    var result =
        aiUseCaseService.summarize(
            access.tenantId(), access.theCase().id(), access.user().id(), request.context());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "AI_SUMMARY_REQUESTED",
        "CASE",
        access.theCase().id(),
        "{\"caseId\":\"" + access.theCase().id() + "\"}");
    return AiUseCaseResponse.from(result);
  }
}
