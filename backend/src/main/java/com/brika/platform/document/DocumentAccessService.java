package com.brika.platform.document;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Reuses CaseAccessService (Sprint 3): PROPERTY_* and DOCUMENT_* are scope (CASE) for BROKER in
 * ADR-RBAC-001, the exact same rule already implemented for CASE_*. A document belonging to another
 * tenant is masked as 404, derived transitively through its case.
 */
@Component
public class DocumentAccessService {

  private final CaseAccessService caseAccessService;
  private final DocumentRepository documentRepository;

  public DocumentAccessService(
      CaseAccessService caseAccessService, DocumentRepository documentRepository) {
    this.caseAccessService = caseAccessService;
    this.documentRepository = documentRepository;
  }

  public DocumentAccessResult requireDocumentAccess(
      Authentication authentication, String permissionCode, UUID documentId) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND", "Document not found."));

    CaseAccessResult caseAccess =
        caseAccessService.requireCaseAccess(authentication, permissionCode, document.caseId());

    if (!document.companyId().equals(caseAccess.tenantId())) {
      throw new ResourceNotFoundException("DOCUMENT_NOT_FOUND", "Document not found.");
    }

    return new DocumentAccessResult(
        caseAccess.user(), caseAccess.tenantId(), caseAccess.theCase(), document);
  }
}
