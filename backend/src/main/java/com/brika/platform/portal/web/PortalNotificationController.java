package com.brika.platform.portal.web;

import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.notification.NotificationRepository;
import com.brika.platform.portal.PortalAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortalNotificationController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final NotificationRepository notificationRepository;
  private final ObjectMapper objectMapper;

  public PortalNotificationController(
      PortalAuthorizationService portalAuthorizationService,
      NotificationRepository notificationRepository,
      ObjectMapper objectMapper) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.notificationRepository = notificationRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/portal/notifications")
  public List<PortalNotificationResponse> list(Authentication authentication) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_NOTIFICATION_READ");
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);
    return notificationRepository.findAllByRecipientClientId(account.clientId()).stream()
        .map(n -> PortalNotificationResponse.from(n, objectMapper))
        .toList();
  }
}
