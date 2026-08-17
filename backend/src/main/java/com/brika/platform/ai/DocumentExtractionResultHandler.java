package com.brika.platform.ai;

import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the outcome of a document extraction attempt — invoked either in-process by
 * LocalAiTaskDispatcher (synchronously, no network) or by the internal worker callback controller
 * (real HTTP path). Both converge on the same persistence + traceability logic, so behavior is
 * identical regardless of transport (D10-5).
 */
@Component
public class DocumentExtractionResultHandler {

  private final DocumentExtractionRepository documentExtractionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentRepository documentRepository;
  private final AiUsageRepository aiUsageRepository;
  private final ObjectMapper objectMapper;

  public DocumentExtractionResultHandler(
      DocumentExtractionRepository documentExtractionRepository,
      DocumentVersionRepository documentVersionRepository,
      DocumentRepository documentRepository,
      AiUsageRepository aiUsageRepository,
      ObjectMapper objectMapper) {
    this.documentExtractionRepository = documentExtractionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.documentRepository = documentRepository;
    this.aiUsageRepository = aiUsageRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * D10-2: status is always NO_PROVIDER for V1 — no real inference ever occurs, so the outcome is
   * never recorded as a successful completion. extractedFields/confidence are structurally present
   * (possibly empty) but never fabricated.
   */
  @Transactional
  public void applyResult(
      UUID extractionId,
      List<Map<String, Object>> extractedFields,
      Map<String, Object> confidence) {
    documentExtractionRepository.applyResult(
        extractionId, "NO_PROVIDER", "none", "none", toJson(extractedFields), toJson(confidence));

    UUID caseId = resolveCaseId(extractionId);
    if (caseId != null) {
      aiUsageRepository.insert(
          documentExtractionRepository.findById(extractionId).orElseThrow().companyId(),
          caseId,
          null, // requester attribution not available on the async worker callback path
          "none",
          "none",
          "DOCUMENT_EXTRACTION",
          null,
          null,
          null);
    }
  }

  private UUID resolveCaseId(UUID extractionId) {
    DocumentExtraction extraction =
        documentExtractionRepository.findById(extractionId).orElseThrow();
    DocumentVersion version =
        documentVersionRepository.findById(extraction.documentVersionId()).orElseThrow();
    Document document = documentRepository.findById(version.documentId()).orElseThrow();
    return document.caseId();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize document extraction result", e);
    }
  }
}
