package com.brika.platform.notification.rabbit;

import com.brika.platform.common.error.ValidationException;
import com.brika.platform.notification.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sprint 26 (20_RABBITMQ_SPECIFICATION.md). Consumes {@code notification.requested} events and
 * writes the in-app notification via {@link NotificationService}. Thin on purpose: it resolves no
 * recipients and contains no Case/Document/Conversation business logic — the recipients were
 * already resolved by the producers before the event was published (same rules as Sprint 25), so
 * those rules live in exactly one place.
 *
 * <p>Acknowledgement: on success the message is acked. On a validation/processing error an
 * exception is thrown so RabbitMQ applies the configured limited retry with backoff and then
 * dead-letters the message (spec §5); the consumer itself never swallows a failure, so a bad
 * message cannot be silently lost.
 */
@Component
@ConditionalOnProperty(name = "brika.notifications.transport", havingValue = "rabbitmq")
public class NotificationRequestedConsumer {

  private final NotificationService notificationService;

  public NotificationRequestedConsumer(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @RabbitListener(queues = RabbitMqConfig.QUEUE_NOTIFICATIONS)
  public void handle(NotificationRequestedEvent event) {
    if (event == null) {
      throw new ValidationException("INVALID_EVENT", "notification.requested payload is empty.");
    }
    // Exactly one of userId/clientId must be set; the service enforces the same rule, but checking
    // here keeps the routing key/semantics of the event self-contained and fails fast.
    if ((event.recipientUserId() == null) == (event.recipientClientId() == null)) {
      throw new ValidationException(
          "INVALID_NOTIFICATION_RECIPIENT",
          "Exactly one of recipientUserId/recipientClientId must be set.");
    }
    if (event.notificationType() == null || event.notificationType().isBlank()) {
      throw new ValidationException("NOTIFICATION_TYPE_REQUIRED", "notificationType is required.");
    }

    notificationService.create(
        event.companyId(),
        event.recipientUserId(),
        event.recipientClientId(),
        event.notificationType(),
        event.payload());
  }
}
