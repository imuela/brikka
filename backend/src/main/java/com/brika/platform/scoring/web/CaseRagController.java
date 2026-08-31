package com.brika.platform.scoring.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.scoring.CaseRagService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRIKKA V2 I2. Read-only qualitative RAG indicator of a case. Reuses {@code SCORING_READ} (the
 * indicator is a view over the scoring result plus other already-readable case data) — no new
 * permission is added, keeping the RBAC catalog stable (ADR-RBAC-001). TENANT + ROLE/PERMISSION +
 * CASE ASSIGNMENT are enforced by {@link CaseAccessService} exactly as every other case-scoped
 * resource, so a case from another tenant resolves to 404 before any RAG data is touched.
 */
@RestController
public class CaseRagController {

  private final CaseAccessService caseAccessService;
  private final CaseClientRepository caseClientRepository;
  private final CaseRagService caseRagService;

  public CaseRagController(
      CaseAccessService caseAccessService,
      CaseClientRepository caseClientRepository,
      CaseRagService caseRagService) {
    this.caseAccessService = caseAccessService;
    this.caseClientRepository = caseClientRepository;
    this.caseRagService = caseRagService;
  }

  @GetMapping("/api/v1/cases/{caseId}/scoring/rag")
  public CaseRagResponse rag(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "SCORING_READ", caseId);
    return CaseRagResponse.from(
        caseRagService.evaluate(
            access.tenantId(),
            access.theCase().id(),
            access.theCase().operationType(),
            holderClientIds(access.theCase().id())));
  }

  private List<UUID> holderClientIds(UUID caseId) {
    return caseClientRepository.findAllByCaseId(caseId).stream()
        .filter(CaseRagController::isHolder)
        .map(CaseClient::clientId)
        .toList();
  }

  private static boolean isHolder(CaseClient caseClient) {
    return caseClient.participationType() == ParticipationType.HOLDER
        || caseClient.participationType() == ParticipationType.CO_HOLDER;
  }
}
