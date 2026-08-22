package com.brika.platform.ai;

import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import com.brika.platform.storage.StorageClient;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 21_AI_V1_SCOPE.md §2.A / §4: creates the extraction record and dispatches it to the AI Worker via
 * whichever AiTaskDispatcher is active (D10-5). Sprint 33: also computes the presigned download URL
 * a real Worker would need to fetch the document's bytes itself — the Worker stays network-isolated
 * with zero storage credentials (ADR-AI-001), so this is the only way it can ever see the content;
 * LocalAiTaskDispatcher ignores it entirely.
 *
 * <p>BUG-002 (found during Sprint 33's own live HTTP-transport validation, fixed same sprint):
 * {@code request} is deliberately NOT {@code @Transactional}. With a real Worker fast enough to
 * call back before a surrounding transaction commits, the callback's own (separate) transaction
 * cannot see the just-inserted PENDING row yet — {@link
 * DocumentExtractionResultHandler#applyResult} fails with {@code NoSuchElementException} resolving
 * the case. {@link DocumentExtractionRepository#insertPending} is already atomic as a single INSERT
 * statement, so nothing here actually needs a shared transaction — dispatching only after that
 * insert has genuinely committed is what makes the callback race-safe, for both dispatchers
 * (LocalAiTaskDispatcher never raced in practice, since its callback runs synchronously in the same
 * thread — but removing the annotation costs it nothing either).
 */
@Service
public class DocumentExtractionService {

  private final DocumentExtractionRepository documentExtractionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final AiTaskDispatcher aiTaskDispatcher;
  private final StorageClient storageClient;

  public DocumentExtractionService(
      DocumentExtractionRepository documentExtractionRepository,
      DocumentVersionRepository documentVersionRepository,
      AiTaskDispatcher aiTaskDispatcher,
      StorageClient storageClient) {
    this.documentExtractionRepository = documentExtractionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.aiTaskDispatcher = aiTaskDispatcher;
    this.storageClient = storageClient;
  }

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
    DocumentDownloadContext downloadContext =
        new DocumentDownloadContext(
            storageClient.presignedDownloadUrl(version.storageKey(), version.originalFilename()),
            version.originalFilename(),
            version.mimeType());
    aiTaskDispatcher.dispatchDocumentExtraction(id, version.id(), companyId, downloadContext);
    return documentExtractionRepository.findById(id).orElseThrow();
  }
}
