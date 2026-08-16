package com.brika.platform.crm.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §6. CLIENT_CREATE/READ/UPDATE for BROKER is scoped TENANT here
 * (Sprint 3 pre-flight Decision B2): direct CRM management of a client is not gated by CASE
 * ASSIGNMENT. CASE ASSIGNMENT applies once a client is used within a CASE, via
 * CaseController/CaseClient endpoints, not here.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

  private final AuthorizationService authorizationService;
  private final ClientRepository clientRepository;

  public ClientController(
      AuthorizationService authorizationService, ClientRepository clientRepository) {
    this.authorizationService = authorizationService;
    this.clientRepository = clientRepository;
  }

  @GetMapping
  public List<ClientResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "CLIENT_READ");
    UUID tenantId = authorizationService.requireTenant(authentication);
    return clientRepository.findAllByCompanyId(tenantId).stream()
        .map(ClientResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public ClientResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "CLIENT_READ");
    UUID tenantId = authorizationService.requireTenant(authentication);
    return ClientResponse.from(requireClientInTenant(id, tenantId));
  }

  @PostMapping
  public ClientResponse create(
      Authentication authentication, @RequestBody CreateClientApiRequest request) {
    authorizationService.requirePermission(authentication, "CLIENT_CREATE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    UUID id =
        clientRepository.insert(
            tenantId, request.firstName(), request.lastName(), request.email(), request.phone());
    return ClientResponse.from(clientRepository.findById(id).orElseThrow());
  }

  @PatchMapping("/{id}")
  public ClientResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateClientApiRequest request) {
    authorizationService.requirePermission(authentication, "CLIENT_UPDATE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    requireClientInTenant(id, tenantId);
    clientRepository.update(
        id, request.firstName(), request.lastName(), request.email(), request.phone());
    return ClientResponse.from(requireClientInTenant(id, tenantId));
  }

  /** A client that exists but belongs to another tenant is reported the same as "not found". */
  private Client requireClientInTenant(UUID id, UUID tenantId) {
    return clientRepository
        .findById(id)
        .filter(client -> tenantId.equals(client.companyId()))
        .orElseThrow(() -> new ResourceNotFoundException("CLIENT_NOT_FOUND", "Client not found."));
  }
}
