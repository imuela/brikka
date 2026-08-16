package com.brika.platform.portal;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.crm.ClientPortalAccount;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Portal counterpart of CaseAccessService, using case_clients (the client actually participates in
 * the case) instead of case_assignments (a broker is assigned to it). Unlike the internal service —
 * where a MANAGER/tenant-scoped mismatch is 403 because the case's mere existence in their own
 * company isn't sensitive — here a case belonging to another client in the same tenant is just as
 * sensitive as a cross-tenant one: masked as 404 in both cases, never 403. A client should never be
 * able to distinguish "not your case" from "doesn't exist" for any case they are not a participant
 * of, including ones belonging to other clients of the same broker.
 */
@Component
public class PortalCaseAccessService {

  private final PortalAuthorizationService portalAuthorizationService;
  private final CaseRepository caseRepository;
  private final CaseClientRepository caseClientRepository;

  public PortalCaseAccessService(
      PortalAuthorizationService portalAuthorizationService,
      CaseRepository caseRepository,
      CaseClientRepository caseClientRepository) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.caseRepository = caseRepository;
    this.caseClientRepository = caseClientRepository;
  }

  public PortalCaseAccessResult requireCaseAccess(
      Authentication authentication, String permissionCode, UUID caseId) {
    portalAuthorizationService.requirePermission(authentication, permissionCode);
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);

    Case theCase =
        caseRepository
            .findById(caseId)
            .filter(c -> account.companyId().equals(c.companyId()))
            .filter(c -> caseClientRepository.exists(c.id(), account.clientId()))
            .orElseThrow(() -> new ResourceNotFoundException("CASE_NOT_FOUND", "Case not found."));

    return new PortalCaseAccessResult(account, account.companyId(), theCase);
  }
}
