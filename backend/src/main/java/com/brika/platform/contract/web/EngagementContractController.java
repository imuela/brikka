package com.brika.platform.contract.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.contract.EngagementContractService;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.web.DocumentVersionResponse;
import com.brika.platform.document.web.GeneratedDocumentResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 32. Reuses DOCUMENT_UPLOAD (generate) / DOCUMENT_READ (view history) — identical RBAC
 * shape already required here (SUPERADMIN/MANAGER/BROKER at case-assignment scope, CLIENT excluded,
 * never reachable from Portal), same reasoning as CaseFeeController. Generating IS conceptually
 * "add a document version" (DOCUMENT_UPLOAD is the sensitive action: injecting content); the
 * underlying Document row is created transparently on first generation only, reusing
 * DOCUMENT_CREATE's identical scope internally without a separate permission check.
 */
@RestController
public class EngagementContractController {

  private static final String DOCUMENT_TYPE_CODE = "ENGAGEMENT_CONTRACT";

  private final CaseAccessService caseAccessService;
  private final EngagementContractService contractService;
  private final DocumentTypeRepository documentTypeRepository;
  private final DocumentRepository documentRepository;
  private final DocumentService documentService;
  private final AuditEventWriter auditEventWriter;

  public EngagementContractController(
      CaseAccessService caseAccessService,
      EngagementContractService contractService,
      DocumentTypeRepository documentTypeRepository,
      DocumentRepository documentRepository,
      DocumentService documentService,
      AuditEventWriter auditEventWriter) {
    this.caseAccessService = caseAccessService;
    this.contractService = contractService;
    this.documentTypeRepository = documentTypeRepository;
    this.documentRepository = documentRepository;
    this.documentService = documentService;
    this.auditEventWriter = auditEventWriter;
  }

  @PostMapping("/api/v1/cases/{caseId}/contract")
  public DocumentVersionResponse generate(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_UPLOAD", caseId);
    UUID actorUserId = access.user().id();
    DocumentVersion version = contractService.generate(access.theCase(), actorUserId);
    auditEventWriter.write(
        access.tenantId(),
        actorUserId,
        null,
        "ENGAGEMENT_CONTRACT_GENERATED",
        "CASE",
        caseId,
        "{\"caseId\":\"" + caseId + "\",\"versionId\":\"" + version.id() + "\"}");
    return DocumentVersionResponse.from(version);
  }

  @GetMapping("/api/v1/cases/{caseId}/contract")
  public GeneratedDocumentResponse get(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_READ", caseId);
    UUID documentTypeId = documentTypeRepository.findByCode(DOCUMENT_TYPE_CODE).orElseThrow().id();
    Optional<Document> document =
        documentRepository.findByCaseIdAndDocumentTypeId(access.theCase().id(), documentTypeId);
    if (document.isEmpty()) {
      return new GeneratedDocumentResponse(null, List.of());
    }
    List<DocumentVersionResponse> versions =
        documentService.listVersions(document.get()).stream()
            .map(DocumentVersionResponse::from)
            .toList();
    return new GeneratedDocumentResponse(document.get().id(), versions);
  }
}
