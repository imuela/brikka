package com.brika.platform.notification.rabbit;

import com.brika.platform.notification.NotificationPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sprint 26. Asynchronous {@link NotificationPublisher}: instead of writing notifications in the
 * caller's transaction, it publishes one {@code notification.requested} event per recipient to
 * RabbitMQ, and {@link NotificationRequestedConsumer} writes them via {@link
 * com.brika.platform.notification.NotificationService}.
 *
 * <p>Recipients are resolved by the producers exactly as in Sprint 25 and passed in explicitly, so
 * this publisher (and the consumer) contains no recipient/business logic. Only one
 * NotificationPublisher bean exists at a time (the transport toggle in application.yml), so the
 * producers are unaware of the transport.
 *
 * <p>Transactionality (spec §6 / Sprint 26 requirement): a notification must never be published if
 * the triggering business transaction fails. Publishing is deferred to after-commit via {@link
 * TransactionSynchronizationManager}: if the transaction rolls back, afterCommit never runs and
 * nothing is published; if there is no active transaction the event is published immediately.
 */
@Component
@ConditionalOnProperty(name = "brika.notifications.transport", havingValue = "rabbitmq")
public class RabbitMqNotificationPublisher implements NotificationPublisher {

  private final RabbitTemplate rabbitTemplate;

  public RabbitMqNotificationPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void notifyUsers(
      UUID companyId, String type, List<UUID> recipientUserIds, Map<String, Object> payload) {
    if (recipientUserIds == null) {
      return;
    }
    for (UUID userId : recipientUserIds) {
      if (userId != null) {
        publish(companyId, userId, null, type, payload);
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
        publish(companyId, null, clientId, type, payload);
      }
    }
  }

  private void publish(
      UUID companyId,
      UUID recipientUserId,
      UUID recipientClientId,
      String type,
      Map<String, Object> payload) {
    NotificationRequestedEvent event =
        new NotificationRequestedEvent(
            UUID.randomUUID(),
            NotificationRequestedEvent.EVENT_TYPE,
            Instant.now(),
            companyId,
            recipientUserId,
            recipientClientId,
            type,
            payload);

    Runnable doPublish =
        () ->
            rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_EVENTS, RabbitMqConfig.ROUTING_NOTIFICATIONS, event);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              doPublish.run();
            }
          });
    } else {
      doPublish.run();
    }
  }
}
