package com.brika.platform.portal.web;

import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.portal.PortalAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortalProfileController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final ClientRepository clientRepository;

  public PortalProfileController(
      PortalAuthorizationService portalAuthorizationService, ClientRepository clientRepository) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.clientRepository = clientRepository;
  }

  @PatchMapping("/api/v1/portal/profile")
  public PortalMeResponse update(
      Authentication authentication, @RequestBody UpdatePortalProfileApiRequest request) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_PROFILE_UPDATE");
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);

    clientRepository.updateContactInfo(account.clientId(), request.email(), request.phone());
    Client client = clientRepository.findById(account.clientId()).orElseThrow();

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
