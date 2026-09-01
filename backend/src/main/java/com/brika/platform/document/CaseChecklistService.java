package com.brika.platform.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRIKKA V2 I1. Materialises the document checklist of a case from the {@code
 * document_requirements} catalog and reports its live state from the documents on the case.
 *
 * <p>Deliberately imports nothing from {@code casemgmt} / {@code crm}: the holder client ids are
 * passed in by the caller (CaseService on status change, CaseChecklistController on read), so this
 * stays a pure {@code document}-package service and there is no service-layer package cycle.
 */
@Service
public class CaseChecklistService {

  private static final String SCOPE_PER_HOLDER = "PER_HOLDER";
  private static final String SCOPE_PER_CASE = "PER_CASE";

  private final DocumentRequirementRepository requirementRepository;
  private final DocumentRequestRepository requestRepository;
  private final DocumentRepository documentRepository;
  private final DocumentTypeRepository documentTypeRepository;
  private final ObjectMapper objectMapper;

  public CaseChecklistService(
      DocumentRequirementRepository requirementRepository,
      DocumentRequestRepository requestRepository,
      DocumentRepository documentRepository,
      DocumentTypeRepository documentTypeRepository,
      ObjectMapper objectMapper) {
    this.requirementRepository = requirementRepository;
    this.requestRepository = requestRepository;
    this.documentRepository = documentRepository;
    this.documentTypeRepository = documentTypeRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Idempotently creates one {@code document_requests} row per active requirement of {@code
   * operationType}: PER_CASE requirements → one row with no holder; PER_HOLDER requirements → one
   * row per holder client id. Safe to call on every entry into DOCUMENTATION (including the
   * backwards ANALYSIS -> DOCUMENTATION edge): existing rows are never duplicated, and rows are
   * never removed if the catalog changes later.
   */
  @Transactional
  public void ensureRequests(
      UUID companyId,
      UUID caseId,
      String operationType,
      List<UUID> holderClientIds,
      UUID actorUserId) {
    for (DocumentRequirement requirement :
        requirementRepository.findActiveByOperationType(operationType)) {
      if (isPerHolder(requirement)) {
        for (UUID clientId : holderClientIds) {
          ensureRequest(companyId, caseId, requirement, clientId, actorUserId);
        }
      } else {
        ensureRequest(companyId, caseId, requirement, null, actorUserId);
      }
    }
  }

  private void ensureRequest(
      UUID companyId,
      UUID caseId,
      DocumentRequirement requirement,
      UUID clientId,
      UUID actorUserId) {
    if (requestRepository.existsForRequirement(caseId, requirement.id(), clientId)) {
      return;
    }
    requestRepository.insert(
        companyId,
        caseId,
        requirement.documentTypeId(),
        clientId,
        null,
        actorUserId,
        requirement.id());
  }

  /**
   * The live checklist. Reconciles each requirement-backed request's status against the actual
   * documents (defence in depth behind {@link DocumentRequestFulfillment}) before reporting.
   */
  @Transactional
  public CaseChecklist checklist(UUID caseId, String operationType, List<UUID> holderClientIds) {
    Map<UUID, DocumentType> typesById = new LinkedHashMap<>();
    for (DocumentType type : documentTypeRepository.findAll()) {
      typesById.put(type.id(), type);
    }

    // (requirementId, clientId-or-null) -> request already materialised for this case.
    Map<RequirementTarget, DocumentRequest> requestsByTarget = new LinkedHashMap<>();
    for (DocumentRequest request : requestRepository.findAllByCaseId(caseId)) {
      if (request.requirementId() != null) {
        requestsByTarget.put(
            new RequirementTarget(request.requirementId(), request.requestedFromClientId()),
            request);
      }
    }

    // (documentTypeId, clientId-or-null) -> best state among the case's documents of that type.
    Map<TypeTarget, ChecklistItemState> stateByTypeTarget = new LinkedHashMap<>();
    for (Document document : documentRepository.findAllByCaseId(caseId)) {
      TypeTarget key = new TypeTarget(document.documentTypeId(), document.clientId());
      stateByTypeTarget.merge(key, stateOf(document), CaseChecklistService::betterState);
    }

    List<CaseChecklistItem> items = new ArrayList<>();
    List<DocumentRequirement> activeRequirements =
        requirementRepository.findActiveByOperationType(operationType);
    java.util.Set<RequirementTarget> handled = new java.util.HashSet<>();

    for (DocumentRequirement requirement : activeRequirements) {
      List<UUID> targets =
          isPerHolder(requirement) ? holderClientIds : java.util.Arrays.asList((UUID) null);
      for (UUID clientId : targets) {
        RequirementTarget target = new RequirementTarget(requirement.id(), clientId);
        handled.add(target);
        DocumentRequest request = requestsByTarget.get(target);
        ChecklistItemState state =
            stateByTypeTarget.getOrDefault(
                new TypeTarget(requirement.documentTypeId(), clientId), ChecklistItemState.MISSING);
        reconcile(request, state);
        items.add(
            toItem(
                requirement.id(),
                request,
                requirement.documentTypeId(),
                requirement.mandatory(),
                clientId,
                state,
                typesById));
      }
    }

    // Requirement-backed requests whose requirement is no longer active — still shown, honestly.
    for (Map.Entry<RequirementTarget, DocumentRequest> entry : requestsByTarget.entrySet()) {
      if (handled.contains(entry.getKey())) {
        continue;
      }
      DocumentRequest request = entry.getValue();
      DocumentRequirement requirement =
          requirementRepository.findById(request.requirementId()).orElse(null);
      if (requirement == null) {
        continue;
      }
      ChecklistItemState state =
          stateByTypeTarget.getOrDefault(
              new TypeTarget(request.documentTypeId(), request.requestedFromClientId()),
              ChecklistItemState.MISSING);
      reconcile(request, state);
      items.add(
          toItem(
              requirement.id(),
              request,
              request.documentTypeId(),
              requirement.mandatory(),
              request.requestedFromClientId(),
              state,
              typesById));
    }

    return CaseChecklist.of(items);
  }

  private void reconcile(DocumentRequest request, ChecklistItemState state) {
    if (request == null) {
      return;
    }
    boolean satisfied = state == ChecklistItemState.APPROVED;
    if (satisfied && request.status() == DocumentRequestStatus.PENDING) {
      requestRepository.updateStatus(request.id(), DocumentRequestStatus.FULFILLED);
    } else if (!satisfied && request.status() == DocumentRequestStatus.FULFILLED) {
      requestRepository.updateStatus(request.id(), DocumentRequestStatus.PENDING);
    }
  }

  private CaseChecklistItem toItem(
      UUID requirementId,
      DocumentRequest request,
      UUID documentTypeId,
      boolean mandatory,
      UUID clientId,
      ChecklistItemState state,
      Map<UUID, DocumentType> typesById) {
    DocumentType type = typesById.get(documentTypeId);
    return new CaseChecklistItem(
        requirementId,
        request == null ? null : request.id(),
        documentTypeId,
        type == null ? null : type.code(),
        type == null ? null : type.name(),
        mandatory,
        clientId,
        state);
  }

  private boolean isPerHolder(DocumentRequirement requirement) {
    return SCOPE_PER_HOLDER.equals(scope(requirement.conditions()));
  }

  /** conditions.appliesTo, defaulting to PER_CASE when absent/blank/malformed. */
  private String scope(String conditionsJson) {
    if (conditionsJson == null || conditionsJson.isBlank()) {
      return SCOPE_PER_CASE;
    }
    try {
      JsonNode node = objectMapper.readTree(conditionsJson).get("appliesTo");
      if (node != null && !node.isNull()) {
        String value = node.asText();
        if (SCOPE_PER_HOLDER.equals(value) || SCOPE_PER_CASE.equals(value)) {
          return value;
        }
      }
    } catch (JsonProcessingException e) {
      // Malformed conditions -> treat as a document of the expediente.
    }
    return SCOPE_PER_CASE;
  }

  private static ChecklistItemState stateOf(Document document) {
    if (document.currentVersionId() == null) {
      return ChecklistItemState.MISSING;
    }
    return switch (document.status()) {
      case APPROVED -> ChecklistItemState.APPROVED;
      case REJECTED -> ChecklistItemState.REJECTED;
      case PENDING -> ChecklistItemState.SUBMITTED;
    };
  }

  private static ChecklistItemState betterState(ChecklistItemState a, ChecklistItemState b) {
    return rank(a) >= rank(b) ? a : b;
  }

  private static int rank(ChecklistItemState state) {
    return switch (state) {
      case APPROVED -> 3;
      case SUBMITTED -> 2;
      case REJECTED -> 1;
      case MISSING -> 0;
    };
  }

  private record RequirementTarget(UUID requirementId, UUID clientId) {
    RequirementTarget {
      Objects.requireNonNull(requirementId);
    }
  }

  private record TypeTarget(UUID documentTypeId, UUID clientId) {
    TypeTarget {
      Objects.requireNonNull(documentTypeId);
    }
  }
}
