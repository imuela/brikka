package com.brika.platform.dossier.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.web.DocumentVersionResponse;
import com.brika.platform.document.web.GeneratedDocumentResponse;
import com.brika.platform.dossier.CaseNarrativeService;
import com.brika.platform.dossier.ViabilityDossierService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 32. Same DOCUMENT_UPLOAD (generate) / DOCUMENT_READ (view history) reuse as
 * EngagementContractController — see its Javadoc for the RBAC justification.
 */
@RestController
public class ViabilityDossierController {

  private static final String DOCUMENT_TYPE_CODE = "VIABILITY_DOSSIER";

  private final CaseAccessService caseAccessService;
  private final ViabilityDossierService dossierService;
  private final CaseNarrativeService caseNarrativeService;
  private final DocumentTypeRepository documentTypeRepository;
  private final DocumentRepository documentRepository;
  private final DocumentService documentService;
  private final AuditEventWriter auditEventWriter;

  public ViabilityDossierController(
      CaseAccessService caseAccessService,
      ViabilityDossierService dossierService,
      CaseNarrativeService caseNarrativeService,
      DocumentTypeRepository documentTypeRepository,
      DocumentRepository documentRepository,
      DocumentService documentService,
      AuditEventWriter auditEventWriter) {
    this.caseAccessService = caseAccessService;
    this.dossierService = dossierService;
    this.caseNarrativeService = caseNarrativeService;
    this.documentTypeRepository = documentTypeRepository;
    this.documentRepository = documentRepository;
    this.documentService = documentService;
    this.auditEventWriter = auditEventWriter;
  }

  @GetMapping("/api/v1/cases/{caseId}/dossier/narrative")
  public CaseNarrativeResponse narrative(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_READ", caseId);
    return CaseNarrativeResponse.from(caseNarrativeService.narrate(access.theCase()));
  }

  @PostMapping("/api/v1/cases/{caseId}/dossier")
  public DocumentVersionResponse generate(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_UPLOAD", caseId);
    UUID actorUserId = access.user().id();
    DocumentVersion version = dossierService.generate(access.theCase(), actorUserId);
    auditEventWriter.write(
        access.tenantId(),
        actorUserId,
        null,
        "VIABILITY_DOSSIER_GENERATED",
        "CASE",
        caseId,
        "{\"caseId\":\"" + caseId + "\",\"versionId\":\"" + version.id() + "\"}");
    return DocumentVersionResponse.from(version);
  }

  @GetMapping("/api/v1/cases/{caseId}/dossier")
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
