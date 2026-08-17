package com.brika.platform.notification.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.identity.User;
import com.brika.platform.notification.Notification;
import com.brika.platform.notification.NotificationDeliveryRepository;
import com.brika.platform.notification.NotificationRepository;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §17C. Notifications are personal: always scoped to the calling
 * user's own recipient_user_id, never to the whole tenant, regardless of how broadly
 * NOTIFICATION_READ is granted — NOTIFICATION_READ authorizes reading notifications, not other
 * users' notifications. notification_deliveries stays read-only here (written only by the
 * dispatcher/workers).
 */
@RestController
public class NotificationController {

  private final AuthorizationService authorizationService;
  private final NotificationRepository notificationRepository;
  private final NotificationDeliveryRepository notificationDeliveryRepository;
  private final ObjectMapper objectMapper;

  public NotificationController(
      AuthorizationService authorizationService,
      NotificationRepository notificationRepository,
      NotificationDeliveryRepository notificationDeliveryRepository,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.notificationRepository = notificationRepository;
    this.notificationDeliveryRepository = notificationDeliveryRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/notifications")
  public List<NotificationResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "NOTIFICATION_READ");
    User user = authorizationService.currentUser(authentication);
    return notificationRepository.findAllByRecipientUserId(user.id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/notifications/{id}/deliveries")
  public List<NotificationDeliveryResponse> deliveries(
      Authentication authentication, @PathVariable UUID id) {
    Notification notification = requireOwnNotification(authentication, id);
    return notificationDeliveryRepository.findAllByNotificationId(notification.id()).stream()
        .map(NotificationDeliveryResponse::from)
        .toList();
  }

  @PatchMapping("/api/v1/notifications/{id}/read")
  public NotificationResponse markRead(Authentication authentication, @PathVariable UUID id) {
    Notification notification = requireOwnNotification(authentication, id);
    notificationRepository.markRead(notification.id());
    return toResponse(notificationRepository.findById(notification.id()).orElseThrow());
  }

  private Notification requireOwnNotification(Authentication authentication, UUID id) {
    authorizationService.requirePermission(authentication, "NOTIFICATION_READ");
    User user = authorizationService.currentUser(authentication);
    Notification notification =
        notificationRepository
            .findById(id)
            .filter(n -> user.id().equals(n.recipientUserId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Not found."));
    return notification;
  }

  private NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.id(),
        notification.type(),
        readJson(notification.payload()),
        notification.readAt(),
        notification.createdAt());
  }

  private Object readJson(String json) {
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
