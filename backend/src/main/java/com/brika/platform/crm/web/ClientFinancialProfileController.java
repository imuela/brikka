package com.brika.platform.crm.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientFinancialProfile;
import com.brika.platform.crm.ClientFinancialProfileService;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 30. Reuses CLIENT_READ/CLIENT_UPDATE — the existing RBAC matrix already grants both to
 * SUPERADMIN/MANAGER/BROKER at the same tenant boundary as the rest of Client data, and no approved
 * documentation calls for a stricter, dedicated permission for the financial profile specifically
 * (Legacy did not gate it separately either). Not reachable from the Portal Client realm at all —
 * PortalCaseAccessService/Portal controllers never import this package — a deliberate Sprint 30
 * decision (03_DOMAIN_SPECIFICATION.md §5 treats the financial profile as controlled business
 * information; Portal's existing self-service surface is intentionally narrow, email/phone only).
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/financial-profile")
public class ClientFinancialProfileController {

  private final AuthorizationService authorizationService;
  private final ClientRepository clientRepository;
  private final ClientFinancialProfileService financialProfileService;
  private final AuditEventWriter auditEventWriter;

  public ClientFinancialProfileController(
      AuthorizationService authorizationService,
      ClientRepository clientRepository,
      ClientFinancialProfileService financialProfileService,
      AuditEventWriter auditEventWriter) {
    this.authorizationService = authorizationService;
    this.clientRepository = clientRepository;
    this.financialProfileService = financialProfileService;
    this.auditEventWriter = auditEventWriter;
  }

  @GetMapping
  public ClientFinancialProfileResponse get(
      Authentication authentication, @PathVariable UUID clientId) {
    authorizationService.requirePermission(authentication, "CLIENT_READ");
    Client client = requireAccessibleClient(authentication, clientId);
    ClientFinancialProfile profile =
        financialProfileService
            .find(client.id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "FINANCIAL_PROFILE_NOT_FOUND",
                        "This client has no financial profile yet."));
    return ClientFinancialProfileResponse.from(profile);
  }

  @PutMapping
  public ClientFinancialProfileResponse upsert(
      Authentication authentication,
      @PathVariable UUID clientId,
      @RequestBody UpsertClientFinancialProfileApiRequest request) {
    authorizationService.requirePermission(authentication, "CLIENT_UPDATE");
    Client client = requireAccessibleClient(authentication, clientId);
    UUID actorUserId = authorizationService.currentUser(authentication).id();
    ClientFinancialProfile saved =
        financialProfileService.upsert(
            client.companyId(),
            client.id(),
            request.maritalStatus(),
            request.dependents(),
            request.employmentType(),
            request.contractType(),
            request.employerName(),
            request.yearsEmployed(),
            request.monthlyIncome(),
            request.savings(),
            request.otherDebtsMonthlyPayment(),
            request.creditCardDebt(),
            request.source(),
            request.status(),
            request.evidenceDocumentVersionId(),
            actorUserId);
    // Sprint 12 D12-2 (ADR-AUDIT-002) precedent: only the client id is recorded, never the actual
    // field values — the values themselves are already reconstructible from
    // client_financial_profile_history, which is access-controlled the same way as the profile
    // itself rather than the broadly-readable audit_events table.
    auditEventWriter.write(
        client.companyId(),
        actorUserId,
        null,
        "CLIENT_FINANCIAL_PROFILE_UPDATED",
        "CLIENT",
        client.id(),
        "{\"clientId\":\"" + client.id() + "\"}");
    return ClientFinancialProfileResponse.from(saved);
  }

  @GetMapping("/history")
  public List<ClientFinancialProfileHistoryResponse> history(
      Authentication authentication, @PathVariable UUID clientId) {
    authorizationService.requirePermission(authentication, "CLIENT_READ");
    Client client = requireAccessibleClient(authentication, clientId);
    return financialProfileService.history(client.id()).stream()
        .map(ClientFinancialProfileHistoryResponse::from)
        .toList();
  }

  /**
   * Same tenant-resolution pattern as ClientController: GLOBAL read for SUPERADMIN, tenant-scoped
   * (masked as 404 across tenants) for everyone else.
   */
  private Client requireAccessibleClient(Authentication authentication, UUID clientId) {
    if (authorizationService.isSuperadmin(authentication)) {
      return clientRepository
          .findById(clientId)
          .orElseThrow(
              () -> new ResourceNotFoundException("CLIENT_NOT_FOUND", "Client not found."));
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    return clientRepository
        .findById(clientId)
        .filter(client -> tenantId.equals(client.companyId()))
        .orElseThrow(() -> new ResourceNotFoundException("CLIENT_NOT_FOUND", "Client not found."));
  }
}
