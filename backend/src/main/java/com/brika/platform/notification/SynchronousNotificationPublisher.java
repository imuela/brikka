package com.brika.platform.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Synchronous implementation of {@link NotificationPublisher}, in the same transaction as the
 * triggering operation (Sprint 3 Decision A pattern). If the caller's operation rolls back, the
 * notification rows roll back with it — no false notifications.
 *
 * <p>Sprint 26: this is the default transport ({@code brika.notifications.transport=sync},
 * matchIfMissing) so existing behaviour and tests stay unchanged. Setting the property to {@code
 * rabbitmq} disables this bean and activates {@code RabbitMqNotificationPublisher} instead; only
 * one NotificationPublisher bean exists at a time.
 */
@Component
@ConditionalOnProperty(
    name = "brika.notifications.transport",
    havingValue = "sync",
    matchIfMissing = true)
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
