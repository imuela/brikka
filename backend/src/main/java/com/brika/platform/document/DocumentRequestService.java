package com.brika.platform.document;

import com.brika.platform.activity.ActivityPublisher;
import com.brika.platform.activity.CaseActivityEvent;
import com.brika.platform.common.error.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequestService {

  private final DocumentRequestRepository documentRequestRepository;
  private final ActivityPublisher activityPublisher;

  public DocumentRequestService(
      DocumentRequestRepository documentRequestRepository, ActivityPublisher activityPublisher) {
    this.documentRequestRepository = documentRequestRepository;
    this.activityPublisher = activityPublisher;
  }

  @Transactional
  public DocumentRequest create(
      UUID tenantId,
      UUID caseId,
      UUID documentTypeId,
      UUID requestedFromClientId,
      Instant dueAt,
      UUID requestedBy,
      UUID requirementId) {
    if (documentTypeId == null) {
      throw new ValidationException(
          "DOCUMENT_TYPE_ID_REQUIRED", "documentTypeId is required to create a document request.");
    }
    UUID id =
        documentRequestRepository.insert(
            tenantId,
            caseId,
            documentTypeId,
            requestedFromClientId,
            dueAt,
            requestedBy,
            requirementId);

    activityPublisher.publish(
        CaseActivityEvent.byUser(
            "document.request.created", tenantId, caseId, requestedBy, "Document request created"));

    return documentRequestRepository.findById(id).orElseThrow();
  }

  public List<DocumentRequest> listByCase(UUID caseId) {
    return documentRequestRepository.findAllByCaseId(caseId);
  }

  /** Sprint 19 (ADR-PROCESS-007): Portal Cliente scoping — see DocumentRequestRepository. */
  public List<DocumentRequest> listByCaseAndClient(UUID caseId, UUID clientId) {
    return documentRequestRepository.findAllByCaseIdAndClientId(caseId, clientId);
  }

  @Transactional
  public DocumentRequest updateStatus(DocumentRequest request, DocumentRequestStatus status) {
    documentRequestRepository.updateStatus(request.id(), status);
    return documentRequestRepository.findById(request.id()).orElseThrow();
  }
}
