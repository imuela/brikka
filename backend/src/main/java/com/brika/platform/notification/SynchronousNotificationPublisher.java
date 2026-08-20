package com.brika.platform.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Synchronous implementation of {@link NotificationPublisher}, in the same transaction as the
 * triggering operation (Sprint 3 Decision A pattern). If the caller's operation rolls back, the
 * notification rows roll back with it — no false notifications.
 */
@Component
public class SynchronousNotificationPublisher implements NotificationPublisher {

  private final NotificationService notificationService;

  public SynchronousNotificationPublisher(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Override
  public void notifyUsers(
      UUID companyId, String type, List<UUID> recipientUserIds, Map<String, Object> payload) {
    if (recipientUserIds == null) {
      return;
    }
    for (UUID userId : recipientUserIds) {
      if (userId != null) {
        notificationService.create(companyId, userId, null, type, payload);
      }
    }
  }

  @Override
  public void notifyClients(
      UUID companyId, String type, List<UUID> recipientClientIds, Map<String, Object> payload) {
    if (recipientClientIds == null) {
      return;
    }
    for (UUID clientId : recipientClientIds) {
      if (clientId != null) {
        notificationService.create(companyId, null, clientId, type, payload);
      }
    }
  }
}
