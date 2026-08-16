package com.brika.platform.casemgmt;

import com.brika.platform.activity.ActivityPublisher;
import com.brika.platform.activity.CaseActivityEvent;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules from 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md. activities is written synchronously
 * in the same transaction as the domain change (Sprint 3 pre-flight Decision A) via
 * ActivityPublisher, whose functional contract is designed to survive a later swap to an
 * async/RabbitMQ implementation without changing this class.
 */
@Service
public class CaseService {

  private final CaseRepository caseRepository;
  private final CaseClientRepository caseClientRepository;
  private final CaseAssignmentRepository caseAssignmentRepository;
  private final CaseStatusHistoryRepository caseStatusHistoryRepository;
  private final ClientRepository clientRepository;
  private final UserRepository userRepository;
  private final ActivityPublisher activityPublisher;

  public CaseService(
      CaseRepository caseRepository,
      CaseClientRepository caseClientRepository,
      CaseAssignmentRepository caseAssignmentRepository,
      CaseStatusHistoryRepository caseStatusHistoryRepository,
      ClientRepository clientRepository,
      UserRepository userRepository,
      ActivityPublisher activityPublisher) {
    this.caseRepository = caseRepository;
    this.caseClientRepository = caseClientRepository;
    this.caseAssignmentRepository = caseAssignmentRepository;
    this.caseStatusHistoryRepository = caseStatusHistoryRepository;
    this.clientRepository = clientRepository;
    this.userRepository = userRepository;
    this.activityPublisher = activityPublisher;
  }

  @Transactional
  public Case createCase(UUID tenantId, UUID createdBy, String operationType) {
    String reference = generateReference();
    UUID id = caseRepository.insert(tenantId, reference, operationType, createdBy);
    activityPublisher.publish(
        CaseActivityEvent.byUser(
            "CaseCreated", tenantId, id, createdBy, "Case " + reference + " created"));
    return caseRepository.findById(id).orElseThrow();
  }

  private String generateReference() {
    // Server-generated only: no document specifies a caller-supplied or sequential reference
    // format, so this is a technical (not business) default — see Sprint 3 gate review.
    return "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  @Transactional
  public Case updateOperationType(Case theCase, String operationType) {
    caseRepository.updateOperationType(theCase.id(), operationType);
    return caseRepository.findById(theCase.id()).orElseThrow();
  }

  /**
   * Normal transitions only — CANCELLED must go through cancel(), which enforces a catalog reason.
   */
  @Transactional
  public Case changeStatus(Case theCase, CaseStatus newStatus, UUID actorUserId, String reason) {
    if (newStatus == CaseStatus.CANCELLED) {
      throw new ValidationException(
          "USE_CANCEL_ENDPOINT", "Use POST /cases/{id}/cancel to cancel a case.");
    }
    CaseStatus current = theCase.status();
    if (current.isTerminal()) {
      throw new ValidationException("CASE_TERMINAL", "Case is in a terminal state; use reopen.");
    }
    if (!CaseWorkflow.canTransition(current, newStatus)) {
      throw new ValidationException(
          "INVALID_TRANSITION", "Cannot transition from " + current + " to " + newStatus + ".");
    }
    if (current == CaseStatus.PRESTUDY && newStatus == CaseStatus.DOCUMENTATION) {
      requireAtLeastOneClient(theCase.id());
    }

    caseRepository.updateStatus(theCase.id(), newStatus, null);
    caseStatusHistoryRepository.insert(
        theCase.companyId(), theCase.id(), current, newStatus, actorUserId, reason);
    String activityType = newStatus == CaseStatus.COMPLETED ? "CaseCompleted" : "CaseStatusChanged";
    activityPublisher.publish(
        CaseActivityEvent.byUser(
            activityType,
            theCase.companyId(),
            theCase.id(),
            actorUserId,
            "Status changed from " + current + " to " + newStatus));
    return caseRepository.findById(theCase.id()).orElseThrow();
  }

  private void requireAtLeastOneClient(UUID caseId) {
    if (caseClientRepository.countByCaseId(caseId) == 0) {
      throw new ValidationException(
          "MISSING_CLIENT", "Case must have at least one client before moving to DOCUMENTATION.");
    }
  }

  @Transactional
  public Case cancel(Case theCase, UUID actorUserId, CancellationReason reason, String comment) {
    CaseStatus current = theCase.status();
    if (current.isTerminal()) {
      throw new ValidationException("CASE_TERMINAL", "Case is already in a terminal state.");
    }
    caseRepository.updateStatus(theCase.id(), CaseStatus.CANCELLED, Instant.now());
    String historyReason =
        comment == null || comment.isBlank() ? reason.name() : reason.name() + ": " + comment;
    caseStatusHistoryRepository.insert(
        theCase.companyId(),
        theCase.id(),
        current,
        CaseStatus.CANCELLED,
        actorUserId,
        historyReason);
    activityPublisher.publish(
        CaseActivityEvent.byUser(
            "CaseCancelled",
            theCase.companyId(),
            theCase.id(),
            actorUserId,
            "Case cancelled: " + reason));
    return caseRepository.findById(theCase.id()).orElseThrow();
  }

  @Transactional
  public Case reopen(Case theCase, UUID actorUserId, String reason, CaseStatus targetStatus) {
    CaseStatus current = theCase.status();
    if (!current.isTerminal()) {
      throw new ValidationException(
          "CASE_NOT_TERMINAL", "Only a terminal case (COMPLETED/CANCELLED) can be reopened.");
    }
    if (targetStatus.isTerminal()) {
      throw new ValidationException(
          "INVALID_TARGET_STATUS", "Reopen target status must not be terminal.");
    }
    caseRepository.updateStatus(theCase.id(), targetStatus, null);
    caseStatusHistoryRepository.insert(
        theCase.companyId(), theCase.id(), current, targetStatus, actorUserId, reason);
    activityPublisher.publish(
        CaseActivityEvent.byUser(
            "CaseReopened",
            theCase.companyId(),
            theCase.id(),
            actorUserId,
            "Case reopened from " + current + " to " + targetStatus));
    return caseRepository.findById(theCase.id()).orElseThrow();
  }

  @Transactional
  public void addClient(
      Case theCase, UUID clientId, ParticipationType participationType, boolean isPrimary) {
    Client client =
        clientRepository
            .findById(clientId)
            .filter(c -> theCase.companyId().equals(c.companyId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("CLIENT_NOT_FOUND", "Client not found."));
    if (caseClientRepository.exists(theCase.id(), client.id())) {
      throw new ValidationException(
          "CLIENT_ALREADY_LINKED", "Client is already a participant of this case.");
    }
    caseClientRepository.insert(theCase.id(), client.id(), participationType, isPrimary);
  }

  @Transactional
  public void removeClient(Case theCase, UUID clientId) {
    if (!caseClientRepository.exists(theCase.id(), clientId)) {
      throw new ResourceNotFoundException(
          "CASE_CLIENT_NOT_FOUND", "This client is not a participant of this case.");
    }
    caseClientRepository.delete(theCase.id(), clientId);
  }

  public List<CaseClient> listClients(Case theCase) {
    return caseClientRepository.findAllByCaseId(theCase.id());
  }

  @Transactional
  public CaseAssignment assignUser(Case theCase, UUID userId, String assignmentType) {
    User assignee =
        userRepository
            .findById(userId)
            .filter(u -> theCase.companyId().equals(u.companyId()))
            .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
    UUID assignmentId =
        caseAssignmentRepository.insert(
            theCase.companyId(), theCase.id(), assignee.id(), assignmentType);
    return caseAssignmentRepository.findAllByCaseId(theCase.id()).stream()
        .filter(a -> a.id().equals(assignmentId))
        .findFirst()
        .orElseThrow();
  }

  public List<CaseAssignment> listAssignments(Case theCase) {
    return caseAssignmentRepository.findAllByCaseId(theCase.id());
  }
}
