package com.brika.platform.communication;

import com.brika.platform.notification.NotificationPublisher;
import com.brika.platform.notification.NotificationRecipients;
import com.brika.platform.notification.NotificationType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 25: message sending lives here instead of in the controllers so the insert and the
 * notification share one transaction — if the message insert fails, no notification is persisted
 * (§12). Recipients come from real relationships: internal users = the case's active assignees
 * (ADR-COMMS-002, the audience of any case conversation), Portal recipients = the CLIENT
 * conversation's active client participants. The author never receives their own notification.
 */
@Service
public class ConversationMessageService {

  private final MessageRepository messageRepository;
  private final NotificationPublisher notificationPublisher;
  private final NotificationRecipients notificationRecipients;

  public ConversationMessageService(
      MessageRepository messageRepository,
      NotificationPublisher notificationPublisher,
      NotificationRecipients notificationRecipients) {
    this.messageRepository = messageRepository;
    this.notificationPublisher = notificationPublisher;
    this.notificationRecipients = notificationRecipients;
  }

  /**
   * An internal user writes into a conversation: the case's other assignees are notified, plus the
   * client participants when it is a CLIENT conversation (Portal inbox).
   */
  @Transactional
  public Message sendFromUser(Conversation conversation, UUID senderUserId, String body) {
    UUID messageId = messageRepository.insertFromUser(conversation.id(), senderUserId, body);

    notificationPublisher.notifyUsers(
        conversation.companyId(),
        NotificationType.NEW_MESSAGE,
        notificationRecipients.assignedUsersExcept(conversation.caseId(), senderUserId),
        payload(conversation, body));

    if ("CLIENT".equals(conversation.type())) {
      notificationPublisher.notifyClients(
          conversation.companyId(),
          NotificationType.NEW_MESSAGE,
          notificationRecipients.clientParticipants(conversation.id()),
          payload(conversation, body));
    }

    return messageRepository.findById(messageId).orElseThrow();
  }

  /** A Portal client writes: the case's internal assignees are notified. */
  @Transactional
  public Message sendFromClient(Conversation conversation, UUID senderClientId, String body) {
    UUID messageId = messageRepository.insertFromClient(conversation.id(), senderClientId, body);

    notificationPublisher.notifyUsers(
        conversation.companyId(),
        NotificationType.NEW_MESSAGE,
        notificationRecipients.assignedUsers(conversation.caseId()),
        payload(conversation, body));

    return messageRepository.findById(messageId).orElseThrow();
  }

  private Map<String, Object> payload(Conversation conversation, String body) {
    return Map.of(
        "caseId", conversation.caseId(),
        "conversationId", conversation.id(),
        "body", body);
  }
}
