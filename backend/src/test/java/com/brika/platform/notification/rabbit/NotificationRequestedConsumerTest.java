package com.brika.platform.notification.rabbit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.brika.platform.common.error.ValidationException;
import com.brika.platform.notification.NotificationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 26. Unit test for {@link NotificationRequestedConsumer}: it forwards a valid event to
 * {@link NotificationService} unchanged and rejects malformed events. The consumer is a thin
 * pass-through, so these checks are deliberately lightweight — the real publisher→broker→consumer
 * flow is covered by the integration test ({@code NotificationAsyncIntegrationIT}).
 */
class NotificationRequestedConsumerTest {

  private final NotificationService notificationService = mock(NotificationService.class);
  private final NotificationRequestedConsumer consumer =
      new NotificationRequestedConsumer(notificationService);

  private NotificationRequestedEvent event(UUID userId, UUID clientId, String type) {
    return new NotificationRequestedEvent(
        UUID.randomUUID(),
        NotificationRequestedEvent.EVENT_TYPE,
        Instant.now(),
        UUID.randomUUID(),
        userId,
        clientId,
        type,
        Map.of("caseId", UUID.randomUUID()));
  }

  @Test
  void forwardsUserEventToService() {
    UUID companyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("caseId", UUID.randomUUID());

    consumer.handle(
        new NotificationRequestedEvent(
            UUID.randomUUID(),
            NotificationRequestedEvent.EVENT_TYPE,
            Instant.now(),
            companyId,
            userId,
            null,
            "CASE_CANCELLED",
            payload));

    verify(notificationService).create(companyId, userId, null, "CASE_CANCELLED", payload);
  }

  @Test
  void forwardsClientEventToService() {
    NotificationRequestedEvent evt = event(null, UUID.randomUUID(), "DOCUMENT_PUBLISHED");

    consumer.handle(evt);

    verify(notificationService)
        .create(
            evt.companyId(), null, evt.recipientClientId(), "DOCUMENT_PUBLISHED", evt.payload());
  }

  @Test
  void rejectsEventWithNoRecipient() {
    assertThatThrownBy(() -> consumer.handle(event(null, null, "CASE_CANCELLED")))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("recipient");
    verifyNoInteractions(notificationService);
  }

  @Test
  void rejectsEventWithBothRecipients() {
    assertThatThrownBy(
            () -> consumer.handle(event(UUID.randomUUID(), UUID.randomUUID(), "CASE_CANCELLED")))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(notificationService);
  }

  @Test
  void rejectsEventWithoutType() {
    assertThatThrownBy(() -> consumer.handle(event(UUID.randomUUID(), null, null)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("notificationType");
    verifyNoInteractions(notificationService);
  }
}
