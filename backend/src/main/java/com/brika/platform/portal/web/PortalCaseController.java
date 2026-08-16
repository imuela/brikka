package com.brika.platform.portal.web;

import com.brika.platform.casemgmt.CaseRepository;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.portal.PortalAuthorizationService;
import com.brika.platform.portal.PortalCaseAccessService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 07_PORTAL_CLIENTE.md: "Consulta de información publicada." — cases carry no internal-only field
 * (notes/scoring/banking data live in tables Portal never queries), so the case's own fields are
 * returned as-is rather than through a separate publication layer that does not exist for cases in
 * the schema (only documents have one, document_publications).
 */
@RestController
public class PortalCaseController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final PortalCaseAccessService portalCaseAccessService;
  private final CaseRepository caseRepository;

  public PortalCaseController(
      PortalAuthorizationService portalAuthorizationService,
      PortalCaseAccessService portalCaseAccessService,
      CaseRepository caseRepository) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.portalCaseAccessService = portalCaseAccessService;
    this.caseRepository = caseRepository;
  }

  @GetMapping("/api/v1/portal/cases")
  public List<PortalCaseResponse> list(Authentication authentication) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_CASE_READ");
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);
    return caseRepository.findAllByClientId(account.companyId(), account.clientId()).stream()
        .map(PortalCaseResponse::from)
        .toList();
  }

  @GetMapping("/api/v1/portal/cases/{id}")
  public PortalCaseResponse get(Authentication authentication, @PathVariable UUID id) {
    return PortalCaseResponse.from(
        portalCaseAccessService
            .requireCaseAccess(authentication, "PORTAL_CASE_READ", id)
            .theCase());
  }
}
