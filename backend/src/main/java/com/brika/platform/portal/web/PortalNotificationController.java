package com.brika.platform.portal.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.notification.Notification;
import com.brika.platform.notification.NotificationRepository;
import com.brika.platform.portal.PortalAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 19 (ADR-PROCESS-007): {@code markRead} closes the gap identified in the Sprint 19
 * definition — the internal {@code NotificationController} has a read-mark endpoint, Portal never
 * did. Same permission as {@code list} (mirrors {@code NotificationController}, where a single
 * NOTIFICATION_READ covers both list and mark-read — no new permission introduced). Ownership is
 * enforced by {@code recipient_client_id}, never by tenant alone, exactly like the internal
 * counterpart enforces it by {@code recipient_user_id}.
 */
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

  @PatchMapping("/api/v1/portal/notifications/{id}/read")
  public PortalNotificationResponse markRead(Authentication authentication, @PathVariable UUID id) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_NOTIFICATION_READ");
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);

    Notification notification =
        notificationRepository
            .findById(id)
            .filter(n -> account.clientId().equals(n.recipientClientId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Not found."));

    notificationRepository.markRead(notification.id());
    return PortalNotificationResponse.from(
        notificationRepository.findById(notification.id()).orElseThrow(), objectMapper);
  }
}
