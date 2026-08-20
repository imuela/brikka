package com.brika.platform.notification.rabbit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 26 (20_RABBITMQ_SPECIFICATION.md §3). A single in-app notification request that flows over
 * RabbitMQ between the (async) {@link com.brika.platform.notification.NotificationPublisher} and
 * the consumer that writes the notification via {@link
 * com.brika.platform.notification.NotificationService}.
 *
 * <p>Recipients are already resolved by the producers (from real relationships, same rules as
 * Sprint 25) before this event is built, so the consumer stays a thin pass-through — it never
 * resolves users/clients and contains no Case/Document/Conversation business logic. This keeps the
 * recipients rules in exactly one place: the producers.
 *
 * <p>The event carries only the minimal identifiers and a simple payload map — never JPA entities
 * and never sensitive data.
 *
 * @param eventId unique id, used for idempotency/deduplication (spec §4).
 * @param eventType always {@code notification.requested}.
 * @param occurredAt timestamp when the triggering operation happened.
 * @param companyId tenant that owns the notification.
 * @param recipientUserId user recipient (exactly one of userId/clientId is set).
 * @param recipientClientId Portal client recipient.
 * @param notificationType the notification type (see {@link
 *     com.brika.platform.notification.NotificationType}).
 * @param payload simple key/value payload to persist.
 */
public record NotificationRequestedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID companyId,
    UUID recipientUserId,
    UUID recipientClientId,
    String notificationType,
    Map<String, Object> payload) {

  public static final String EVENT_TYPE = "notification.requested";

  public NotificationRequestedEvent {
    if (eventId == null) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (companyId == null) {
      throw new IllegalArgumentException("companyId is required");
    }
  }
}
