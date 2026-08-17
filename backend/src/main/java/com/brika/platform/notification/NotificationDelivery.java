package com.brika.platform.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-NOTIF-001: one row per channel attempted for a given Notification. Read-only via API —
 * written exclusively by channel workers (NotificationDeliveryDispatcher).
 */
public record NotificationDelivery(
    UUID id,
    UUID notificationId,
    String channel,
    String status,
    String providerReference,
    Instant sentAt,
    String failedReason,
    Instant createdAt) {}
