package com.brika.platform.notification;

/**
 * Seam between "a Notification was created" and "how it reaches each channel's
 * notification_deliveries row". Sprint 8 pre-flight D8-1: implemented synchronously (same pattern,
 * same justification, as ActivityPublisher/SynchronousActivityPublisher in Sprint 3) — no RabbitMQ
 * yet (20_RABBITMQ_SPECIFICATION.md describes the target async architecture for a later hardening
 * sprint). Callers depend only on this interface.
 */
public interface NotificationDeliveryDispatcher {

  void dispatch(Notification notification);
}
