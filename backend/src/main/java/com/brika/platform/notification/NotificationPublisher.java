package com.brika.platform.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Seam between "a domain event happened with a resolved set of recipients" and "those recipients
 * get a Notification". Mirrors the ActivityPublisher/SynchronousActivityPublisher pattern (Sprint 3
 * Decision A): callers depend only on this interface, so the delivery mechanism can later swap to
 * async/RabbitMQ (20_RABBITMQ_SPECIFICATION.md) without changing the producers.
 *
 * <p>Recipients are resolved by the producers from real relationships (case assignments, case
 * clients, conversation participants) and passed in explicitly; this keeps the publisher a thin
 * pass-through to {@link NotificationService}. Empty recipient lists are a no-op — no notification
 * is created for nobody.
 */
public interface NotificationPublisher {

  /** Creates one IN_APP notification per user in {@code recipientUserIds} (same company). */
  void notifyUsers(
      UUID companyId,
      String type,
      java.util.List<UUID> recipientUserIds,
      Map<String, Object> payload);

  /**
   * Creates one IN_APP notification per Portal client in {@code recipientClientIds} (same company).
   */
  void notifyClients(
      UUID companyId,
      String type,
      java.util.List<UUID> recipientClientIds,
      Map<String, Object> payload);
}
