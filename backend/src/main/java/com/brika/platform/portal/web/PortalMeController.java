package com.brika.platform.portal.web;

import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.crm.ClientPortalAccountRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.portal.PortalAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortalMeController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final ClientRepository clientRepository;
  private final ClientPortalAccountRepository clientPortalAccountRepository;

  public PortalMeController(
      PortalAuthorizationService portalAuthorizationService,
      ClientRepository clientRepository,
      ClientPortalAccountRepository clientPortalAccountRepository) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.clientRepository = clientRepository;
    this.clientPortalAccountRepository = clientPortalAccountRepository;
  }

  @GetMapping("/api/v1/portal/me")
  public PortalMeResponse me(Authentication authentication) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_PROFILE_READ");
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);
    Client client = clientRepository.findById(account.clientId()).orElseThrow();

    clientPortalAccountRepository.updateLastLoginAt(account.id());

    return new PortalMeResponse(
        client.id(),
        client.firstName(),
        client.lastName(),
        client.email(),
        client.phone(),
        account.status(),
        account.lastLoginAt());
  }
}
