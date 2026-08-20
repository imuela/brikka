package com.brika.platform.notification;

import com.brika.platform.casemgmt.CaseAssignmentRepository;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.communication.ConversationParticipantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves notification recipients from the relationships that already exist in the model — no
 * invented business rules (Sprint 25 §7). Users are the case's active assignees (CASE ASSIGNMENT is
 * the access relationship for every case-scoped resource, ADR-COMMS-002); Portal recipients are the
 * case's clients or a CLIENT conversation's active client participants.
 */
@Component
public class NotificationRecipients {

  private final CaseAssignmentRepository caseAssignmentRepository;
  private final CaseClientRepository caseClientRepository;
  private final ConversationParticipantRepository conversationParticipantRepository;

  public NotificationRecipients(
      CaseAssignmentRepository caseAssignmentRepository,
      CaseClientRepository caseClientRepository,
      ConversationParticipantRepository conversationParticipantRepository) {
    this.caseAssignmentRepository = caseAssignmentRepository;
    this.caseClientRepository = caseClientRepository;
    this.conversationParticipantRepository = conversationParticipantRepository;
  }

  /** Active assignees of the case, excluding {@code excludeUserId} (null = exclude nobody). */
  public List<UUID> assignedUsersExcept(UUID caseId, UUID excludeUserId) {
    return caseAssignmentRepository.findAllByCaseId(caseId).stream()
        .filter(a -> a.active())
        .map(a -> a.userId())
        .filter(userId -> !userId.equals(excludeUserId))
        .toList();
  }

  /** Active assignees of the case. */
  public List<UUID> assignedUsers(UUID caseId) {
    return caseAssignmentRepository.findAllByCaseId(caseId).stream()
        .filter(a -> a.active())
        .map(a -> a.userId())
        .toList();
  }

  /** Client ids participating in the case (Portal recipients). */
  public List<UUID> caseClients(UUID caseId) {
    return caseClientRepository.findAllByCaseId(caseId).stream().map(c -> c.clientId()).toList();
  }

  /** Active client participants of a CLIENT conversation (Portal recipients). */
  public List<UUID> clientParticipants(UUID conversationId) {
    return conversationParticipantRepository.findActiveByConversationId(conversationId).stream()
        .map(p -> p.participantClientId())
        .filter(clientId -> clientId != null)
        .toList();
  }
}
