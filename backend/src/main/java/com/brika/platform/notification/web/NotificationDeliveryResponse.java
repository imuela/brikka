package com.brika.platform.notification.web;

import com.brika.platform.notification.NotificationDelivery;
import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryResponse(
    UUID id,
    String channel,
    String status,
    String providerReference,
    Instant sentAt,
    String failedReason,
    Instant createdAt) {

  public static NotificationDeliveryResponse from(NotificationDelivery delivery) {
    return new NotificationDeliveryResponse(
        delivery.id(),
        delivery.channel(),
        delivery.status(),
        delivery.providerReference(),
        delivery.sentAt(),
        delivery.failedReason(),
        delivery.createdAt());
  }
}
