package com.brika.platform.ai.web;

import com.brika.platform.ai.DocumentExtraction;
import com.brika.platform.ai.DocumentExtractionRepository;
import com.brika.platform.ai.DocumentExtractionService;
import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.DocumentAccessResult;
import com.brika.platform.document.DocumentAccessService;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 21_AI_V1_SCOPE.md §2.A. Reuses DocumentAccessService (Sprint 4) — same TENANT + ROLE/PERMISSION +
 * CASE ASSIGNMENT pipeline as every other document-scoped resource, gated by AI_DOCUMENT_ANALYZE
 * (D10-1). The extraction dispatch never accepts a fabricated result from the client — always built
 * server-side by DocumentExtractionService/AiTaskDispatcher.
 */
@RestController
public class AiDocumentExtractionController {

  private final DocumentAccessService documentAccessService;
  private final DocumentExtractionService documentExtractionService;
  private final DocumentExtractionRepository documentExtractionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final ObjectMapper objectMapper;
  private final AuditEventWriter auditEventWriter;

  public AiDocumentExtractionController(
      DocumentAccessService documentAccessService,
      DocumentExtractionService documentExtractionService,
      DocumentExtractionRepository documentExtractionRepository,
      DocumentVersionRepository documentVersionRepository,
      ObjectMapper objectMapper,
      AuditEventWriter auditEventWriter) {
    this.documentAccessService = documentAccessService;
    this.documentExtractionService = documentExtractionService;
    this.documentExtractionRepository = documentExtractionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.objectMapper = objectMapper;
    this.auditEventWriter = auditEventWriter;
  }

  @PostMapping("/api/v1/documents/{documentId}/ai/document-extractions")
  public DocumentExtractionResponse create(
      Authentication authentication,
      @PathVariable UUID documentId,
      @RequestBody CreateDocumentExtractionApiRequest request) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(
            authentication, "AI_DOCUMENT_ANALYZE", documentId);
    if (request.documentVersionId() == null) {
      throw new ValidationException(
          "DOCUMENT_VERSION_ID_REQUIRED", "documentVersionId is required.");
    }
    DocumentExtraction extraction =
        documentExtractionService.request(
            access.tenantId(), documentId, request.documentVersionId());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "AI_DOCUMENT_EXTRACTION_REQUESTED",
        "DOCUMENT",
        documentId,
        "{\"documentId\":\""
            + documentId
            + "\",\"documentVersionId\":\""
            + request.documentVersionId()
            + "\"}");
    return toResponse(extraction);
  }

  @GetMapping("/api/v1/documents/{documentId}/ai/document-extractions")
  public List<DocumentExtractionResponse> list(
      Authentication authentication, @PathVariable UUID documentId) {
    documentAccessService.requireDocumentAccess(authentication, "AI_DOCUMENT_ANALYZE", documentId);
    return documentExtractionRepository.findAllByDocumentId(documentId).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/ai/document-extractions/{id}")
  public DocumentExtractionResponse get(Authentication authentication, @PathVariable UUID id) {
    DocumentExtraction extraction =
        documentExtractionRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("DOCUMENT_EXTRACTION_NOT_FOUND", "Not found."));
    DocumentVersion version =
        documentVersionRepository
            .findById(extraction.documentVersionId())
            .orElseThrow(
                () -> new ResourceNotFoundException("DOCUMENT_EXTRACTION_NOT_FOUND", "Not found."));

    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(
            authentication, "AI_DOCUMENT_ANALYZE", version.documentId());
    if (!extraction.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("DOCUMENT_EXTRACTION_NOT_FOUND", "Not found.");
    }

    return toResponse(extraction);
  }

  private DocumentExtractionResponse toResponse(DocumentExtraction extraction) {
    return new DocumentExtractionResponse(
        extraction.id(),
        extraction.documentVersionId(),
        extraction.status(),
        extraction.provider(),
        extraction.model(),
        readJson(extraction.extractedDataJson()),
        readJson(extraction.confidenceJson()),
        extraction.validatedBy(),
        extraction.validatedAt(),
        extraction.createdAt());
  }

  private Object readJson(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
