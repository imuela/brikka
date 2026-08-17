package com.brika.platform.ai;

import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 21_AI_V1_SCOPE.md §2.A / §4: creates the extraction record and dispatches it to the AI Worker via
 * whichever AiTaskDispatcher is active (D10-5).
 */
@Service
public class DocumentExtractionService {

  private final DocumentExtractionRepository documentExtractionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final AiTaskDispatcher aiTaskDispatcher;

  public DocumentExtractionService(
      DocumentExtractionRepository documentExtractionRepository,
      DocumentVersionRepository documentVersionRepository,
      AiTaskDispatcher aiTaskDispatcher) {
    this.documentExtractionRepository = documentExtractionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.aiTaskDispatcher = aiTaskDispatcher;
  }

  @Transactional
  public DocumentExtraction request(UUID companyId, UUID documentId, UUID documentVersionId) {
    DocumentVersion version =
        documentVersionRepository
            .findById(documentVersionId)
            .filter(v -> v.documentId().equals(documentId))
            .orElseThrow(
                () ->
                    new ValidationException(
                        "DOCUMENT_VERSION_NOT_IN_DOCUMENT",
                        "documentVersionId does not belong to this document."));

    UUID id = documentExtractionRepository.insertPending(companyId, version.id());
    aiTaskDispatcher.dispatchDocumentExtraction(id, version.id(), companyId);
    return documentExtractionRepository.findById(id).orElseThrow();
  }
}
