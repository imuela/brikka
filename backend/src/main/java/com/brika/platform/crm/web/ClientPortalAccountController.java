package com.brika.platform.crm.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientPortalAccountRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-PORTAL-AUTH-001: this endpoint registers an externalIdentityId as this Portal account's
 * opaque login identifier (Sprint 22 cierre: Brika's own auth, no external identity provider) — it
 * never creates a client_portal_accounts row in anything but ACTIVE status (no PENDING/invitation
 * flow in Sprint 7, approved explicitly). No CHECK/UNIQUE constraint backs the uniqueness rules
 * below — client_portal_accounts has none on external_identity_id or client_id (verified against
 * V1__initial_schema.sql; same gap already existed for users.external_identity_id, never a UNIQUE
 * index either). Enforced here at the application layer only; there is a theoretical race window
 * between the lookup and the insert. Flagged as technical debt in the Sprint 7 gate review, not
 * silently fixed with an uninstructed migration, per explicit instruction.
 */
@RestController
public class ClientPortalAccountController {

  private final AuthorizationService authorizationService;
  private final ClientRepository clientRepository;
  private final ClientPortalAccountRepository clientPortalAccountRepository;

  public ClientPortalAccountController(
      AuthorizationService authorizationService,
      ClientRepository clientRepository,
      ClientPortalAccountRepository clientPortalAccountRepository) {
    this.authorizationService = authorizationService;
    this.clientRepository = clientRepository;
    this.clientPortalAccountRepository = clientPortalAccountRepository;
  }

  @PostMapping("/api/v1/clients/{clientId}/portal-account")
  public ClientPortalAccountResponse create(
      Authentication authentication,
      @PathVariable UUID clientId,
      @RequestBody CreatePortalAccountApiRequest request) {
    authorizationService.requirePermission(authentication, "CLIENT_PORTAL_ACCOUNT_CREATE");
    UUID tenantId = authorizationService.requireTenant(authentication);

    Client client =
        clientRepository
            .findById(clientId)
            .filter(c -> tenantId.equals(c.companyId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("CLIENT_NOT_FOUND", "Client not found."));

    String externalIdentityId = request.externalIdentityId();
    if (externalIdentityId == null || externalIdentityId.isBlank()) {
      throw new ValidationException(
          "EXTERNAL_IDENTITY_ID_REQUIRED", "externalIdentityId is required.");
    }

    if (clientPortalAccountRepository.findByClientId(client.id()).isPresent()) {
      throw new ValidationException(
          "PORTAL_ACCOUNT_ALREADY_EXISTS", "This client already has a Portal account.");
    }
    if (clientPortalAccountRepository.findByExternalIdentityId(externalIdentityId).isPresent()) {
      throw new ValidationException(
          "PORTAL_IDENTITY_ALREADY_LINKED", "This identity is already linked to a Portal account.");
    }

    UUID id =
        clientPortalAccountRepository.insert(tenantId, client.id(), externalIdentityId, "ACTIVE");
    return ClientPortalAccountResponse.from(
        clientPortalAccountRepository.findById(id).orElseThrow());
  }
}
