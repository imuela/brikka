package com.brika.platform.notification.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 26 (20_RABBITMQ_SPECIFICATION.md). RabbitMQ topology for the async notification transport.
 * Only activated when {@code brika.notifications.transport=rabbitmq}, so the default sync transport
 * never creates a broker connection (CI and the rest of the test suite stay broker-free).
 *
 * <p>Naming follows the spec: separate queues per responsibility (one queue for notification
 * events). A single topic exchange hosts every event type; {@code notification.requested} routes to
 * the notifications queue. A dead-letter exchange/queue implements the spec's limited-retry +
 * backoff + DLQ policy (§5): after retries are exhausted the listener rejects the message, which
 * the broker then routes to the DLQ instead of dropping it.
 */
@Configuration
@ConditionalOnProperty(name = "brika.notifications.transport", havingValue = "rabbitmq")
public class RabbitMqConfig {

  public static final String EXCHANGE_EVENTS = "brika.events";
  public static final String QUEUE_NOTIFICATIONS = "brika.notifications.queue";
  public static final String EXCHANGE_DLX = "brika.events.dlx";
  public static final String QUEUE_NOTIFICATIONS_DLQ = "brika.notifications.dlq";
  static final String ROUTING_NOTIFICATIONS = "notification.requested";
  static final String DLQ_ROUTING_NOTIFICATIONS = "notification.requested.dlq";

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(EXCHANGE_EVENTS);
  }

  @Bean
  public TopicExchange notificationsDlx() {
    return new TopicExchange(EXCHANGE_DLX);
  }

  @Bean
  public Queue notificationsQueue() {
    return QueueBuilder.durable(QUEUE_NOTIFICATIONS)
        .deadLetterExchange(EXCHANGE_DLX)
        .deadLetterRoutingKey(DLQ_ROUTING_NOTIFICATIONS)
        .build();
  }

  @Bean
  public Queue notificationsDlq() {
    return QueueBuilder.durable(QUEUE_NOTIFICATIONS_DLQ).build();
  }

  @Bean
  public Binding notificationsBinding() {
    return BindingBuilder.bind(notificationsQueue())
        .to(eventsExchange())
        .with(ROUTING_NOTIFICATIONS);
  }

  @Bean
  public Binding notificationsDlqBinding() {
    return BindingBuilder.bind(notificationsDlq())
        .to(notificationsDlx())
        .with(DLQ_ROUTING_NOTIFICATIONS);
  }

  @Bean
  public Jackson2JsonMessageConverter notificationMessageConverter(ObjectMapper objectMapper) {
    // Reuse Spring's ObjectMapper (already has JavaTimeModule) so Instant/records round-trip.
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public RabbitTemplate notificationRabbitTemplate(
      ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter notificationMessageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(notificationMessageConverter);
    return rabbitTemplate;
  }
}
