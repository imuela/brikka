package com.brika.platform.portal.web;

import com.brika.platform.notification.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PortalNotificationResponse(
    UUID id, String type, Map<String, Object> payload, Instant readAt, Instant createdAt) {

  public static PortalNotificationResponse from(
      Notification notification, ObjectMapper objectMapper) {
    return new PortalNotificationResponse(
        notification.id(),
        notification.type(),
        readPayload(notification.payload(), objectMapper),
        notification.readAt(),
        notification.createdAt());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readPayload(String json, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
