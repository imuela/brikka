package com.brika.platform.document.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.DocumentRequest;
import com.brika.platform.document.DocumentRequestRepository;
import com.brika.platform.document.DocumentRequestService;
import com.brika.platform.document.DocumentRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §10. A single permission, DOCUMENT_REQUEST, covers the whole
 * capability — the catalog does not split create/read/update for this resource (ADR-RBAC-001).
 */
@RestController
public class DocumentRequestController {

  private final CaseAccessService caseAccessService;
  private final DocumentRequestRepository documentRequestRepository;
  private final DocumentRequestService documentRequestService;

  public DocumentRequestController(
      CaseAccessService caseAccessService,
      DocumentRequestRepository documentRequestRepository,
      DocumentRequestService documentRequestService) {
    this.caseAccessService = caseAccessService;
    this.documentRequestRepository = documentRequestRepository;
    this.documentRequestService = documentRequestService;
  }

  @GetMapping("/api/v1/cases/{caseId}/document-requests")
  public List<DocumentRequestResponse> list(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_REQUEST", caseId);
    return documentRequestService.listByCase(access.theCase().id()).stream()
        .map(DocumentRequestResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/document-requests")
  public DocumentRequestResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateDocumentRequestApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_REQUEST", caseId);
    DocumentRequest created =
        documentRequestService.create(
            access.tenantId(),
            access.theCase().id(),
            request.documentTypeId(),
            request.requestedFromClientId(),
            request.dueAt(),
            access.user().id(),
            request.requirementId());
    return DocumentRequestResponse.from(created);
  }

  @PatchMapping("/api/v1/document-requests/{id}")
  public DocumentRequestResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateDocumentRequestApiRequest request) {
    DocumentRequest documentRequest =
        documentRequestRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("DOCUMENT_REQUEST_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(
            authentication, "DOCUMENT_REQUEST", documentRequest.caseId());
    if (!documentRequest.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("DOCUMENT_REQUEST_NOT_FOUND", "Not found.");
    }

    DocumentRequestStatus status = parseStatus(request.status());
    return DocumentRequestResponse.from(
        documentRequestService.updateStatus(documentRequest, status));
  }

  private DocumentRequestStatus parseStatus(String value) {
    try {
      return DocumentRequestStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("INVALID_STATUS", "Unknown document request status: " + value);
    }
  }
}
