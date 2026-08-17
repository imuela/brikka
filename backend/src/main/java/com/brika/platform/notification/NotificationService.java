package com.brika.platform.notification;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-NOTIF-001. Internal capability: creates a Notification and dispatches its deliveries. Sprint
 * 8 (D8-3): no domain event (case.status.changed, document.uploaded, etc.) is wired to call this
 * yet — which business event should produce which notification.type/recipient is undefined in any
 * document, so no such wiring exists here. This service is deliberately caller-driven, ready for
 * whichever future sprint's ADR resolves that matrix.
 */
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationDeliveryDispatcher dispatcher;
  private final ObjectMapper objectMapper;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationDeliveryDispatcher dispatcher,
      ObjectMapper objectMapper) {
    this.notificationRepository = notificationRepository;
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Notification create(
      UUID companyId, UUID recipientUserId, UUID recipientClientId, String type, Object payload) {
    if ((recipientUserId == null) == (recipientClientId == null)) {
      throw new ValidationException(
          "INVALID_NOTIFICATION_RECIPIENT",
          "Exactly one of recipientUserId/recipientClientId must be set.");
    }
    if (type == null || type.isBlank()) {
      throw new ValidationException("NOTIFICATION_TYPE_REQUIRED", "type is required.");
    }

    UUID id =
        notificationRepository.insert(
            companyId, recipientUserId, recipientClientId, type, toJson(payload));
    Notification notification = notificationRepository.findById(id).orElseThrow();
    dispatcher.dispatch(notification);
    return notification;
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? java.util.Map.of() : payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize notification payload", e);
    }
  }
}
