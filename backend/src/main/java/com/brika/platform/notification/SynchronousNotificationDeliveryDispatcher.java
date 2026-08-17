package com.brika.platform.notification;

import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SynchronousNotificationDeliveryDispatcher implements NotificationDeliveryDispatcher {

  private final NotificationDeliveryRepository notificationDeliveryRepository;
  private final EmailSender emailSender;
  private final UserRepository userRepository;
  private final ClientRepository clientRepository;

  public SynchronousNotificationDeliveryDispatcher(
      NotificationDeliveryRepository notificationDeliveryRepository,
      EmailSender emailSender,
      UserRepository userRepository,
      ClientRepository clientRepository) {
    this.notificationDeliveryRepository = notificationDeliveryRepository;
    this.emailSender = emailSender;
    this.userRepository = userRepository;
    this.clientRepository = clientRepository;
  }

  @Override
  public void dispatch(Notification notification) {
    dispatchInApp(notification);
    dispatchEmail(notification);
  }

  /**
   * IN_APP has no external dependency: the notification is already queryable via GET /notifications
   * the instant it's inserted, so the delivery record is immediate bookkeeping.
   */
  private void dispatchInApp(Notification notification) {
    notificationDeliveryRepository.insert(
        notification.id(), "IN_APP", "SENT", null, Instant.now(), null);
  }

  private void dispatchEmail(Notification notification) {
    String email = resolveRecipientEmail(notification);
    if (email == null) {
      notificationDeliveryRepository.insert(
          notification.id(), "EMAIL", "FAILED", null, null, "Recipient has no email on file.");
      return;
    }

    EmailSendResult result =
        emailSender.send(
            email, "Brika notification: " + notification.type(), notification.payload());

    if (result.sent()) {
      notificationDeliveryRepository.insert(
          notification.id(), "EMAIL", "SENT", result.providerReference(), Instant.now(), null);
    } else {
      notificationDeliveryRepository.insert(
          notification.id(), "EMAIL", "FAILED", null, null, result.failureReason());
    }
  }

  private String resolveRecipientEmail(Notification notification) {
    if (notification.recipientUserId() != null) {
      return userRepository
          .findById(notification.recipientUserId())
          .map(u -> u.email())
          .orElse(null);
    }
    return clientRepository
        .findById(notification.recipientClientId())
        .map(c -> c.email())
        .orElse(null);
  }
}
