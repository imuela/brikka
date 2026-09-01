package com.brika.platform.document.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.document.CaseChecklistService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRIKKA V2 I1. Read-only view over the case's requirement-backed document requests and their live
 * state. Reuses the DOCUMENT_REQUEST permission (the checklist is a view of document requests) — no
 * new permission is added (RBAC catalog stays stable, ADR-RBAC-001).
 */
@RestController
public class CaseChecklistController {

  private final CaseAccessService caseAccessService;
  private final CaseClientRepository caseClientRepository;
  private final CaseChecklistService caseChecklistService;

  public CaseChecklistController(
      CaseAccessService caseAccessService,
      CaseClientRepository caseClientRepository,
      CaseChecklistService caseChecklistService) {
    this.caseAccessService = caseAccessService;
    this.caseClientRepository = caseClientRepository;
    this.caseChecklistService = caseChecklistService;
  }

  @GetMapping("/api/v1/cases/{caseId}/checklist")
  public CaseChecklistResponse checklist(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_REQUEST", caseId);
    List<UUID> holderClientIds = holderClientIds(access.theCase().id());
    return CaseChecklistResponse.from(
        caseChecklistService.checklist(
            access.theCase().id(), access.theCase().operationType(), holderClientIds));
  }

  private List<UUID> holderClientIds(UUID caseId) {
    return caseClientRepository.findAllByCaseId(caseId).stream()
        .filter(CaseChecklistController::isHolder)
        .map(CaseClient::clientId)
        .toList();
  }

  private static boolean isHolder(CaseClient caseClient) {
    return caseClient.participationType() == ParticipationType.HOLDER
        || caseClient.participationType() == ParticipationType.CO_HOLDER;
  }
}
