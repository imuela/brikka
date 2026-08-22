package com.brika.platform.document;

import com.brika.platform.activity.ActivityPublisher;
import com.brika.platform.activity.CaseActivityEvent;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.notification.NotificationPublisher;
import com.brika.platform.notification.NotificationRecipients;
import com.brika.platform.notification.NotificationType;
import com.brika.platform.storage.DocumentStorageKey;
import com.brika.platform.storage.StorageClient;
import com.brika.platform.storage.StorageProperties;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Only "document.uploaded" is written to activities: it is the sole document-related event name
 * 20_RABBITMQ_SPECIFICATION.md §2 documents explicitly. Review/publish/unpublish are not invented
 * activity entries beyond what is documented (Sprint 4 gate review).
 */
@Service
public class DocumentService {

  /**
   * Sprint 4 pre-flight decision: provisional technical default, not a documented business catalog.
   */
  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(
          "application/pdf",
          "image/jpeg",
          "image/png",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          // Sprint 32: no PDF/template-engine dependency exists in this codebase (confirmed by
          // inspection before implementing) — the dossier/contract generators produce a
          // self-contained HTML document instead of pulling in a new library for V1.
          "text/html");

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentPublicationRepository documentPublicationRepository;
  private final StorageClient storageClient;
  private final StorageProperties storageProperties;
  private final ActivityPublisher activityPublisher;
  private final NotificationPublisher notificationPublisher;
  private final NotificationRecipients notificationRecipients;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentVersionRepository documentVersionRepository,
      DocumentPublicationRepository documentPublicationRepository,
      StorageClient storageClient,
      StorageProperties storageProperties,
      ActivityPublisher activityPublisher,
      NotificationPublisher notificationPublisher,
      NotificationRecipients notificationRecipients) {
    this.documentRepository = documentRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.documentPublicationRepository = documentPublicationRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
    this.activityPublisher = activityPublisher;
    this.notificationPublisher = notificationPublisher;
    this.notificationRecipients = notificationRecipients;
  }

  @Transactional
  public Document createDocument(UUID tenantId, UUID caseId, UUID documentTypeId) {
    UUID id = documentRepository.insert(tenantId, caseId, documentTypeId);
    return documentRepository.findById(id).orElseThrow();
  }

  @Transactional
  public DocumentVersion uploadVersion(
      Document document,
      byte[] content,
      String originalFilename,
      String declaredMimeType,
      UUID uploadedBy) {
    validateUpload(content, declaredMimeType);

    UUID versionId = UUID.randomUUID();
    int versionNumber = documentVersionRepository.nextVersionNumber(document.id());
    String storageKey =
        DocumentStorageKey.build(
            document.companyId(), document.caseId(), document.id(), versionId, originalFilename);
    String checksum = sha256(content);

    storageClient.upload(storageKey, content, declaredMimeType);
    documentVersionRepository.insert(
        versionId,
        document.id(),
        versionNumber,
        storageKey,
        originalFilename,
        declaredMimeType,
        content.length,
        checksum,
        uploadedBy);
    documentRepository.setCurrentVersionAndStatus(document.id(), versionId, ReviewStatus.PENDING);

    activityPublisher.publish(
        CaseActivityEvent.byUser(
            "document.uploaded",
            document.companyId(),
            document.caseId(),
            uploadedBy,
            "Document version " + versionNumber + " uploaded (" + originalFilename + ")"));

    notificationPublisher.notifyUsers(
        document.companyId(),
        NotificationType.DOCUMENT_UPLOADED,
        notificationRecipients.assignedUsersExcept(document.caseId(), uploadedBy),
        Map.of(
            "caseId",
            document.caseId(),
            "documentId",
            document.id(),
            "versionNumber",
            versionNumber,
            "filename",
            originalFilename));

    return documentVersionRepository.findById(versionId).orElseThrow();
  }

  /**
   * Portal Cliente upload (Sprint 7, decision D3 / V12): mirrors uploadVersion for a client actor.
   */
  @Transactional
  public DocumentVersion uploadVersionFromClient(
      Document document,
      byte[] content,
      String originalFilename,
      String declaredMimeType,
      UUID uploadedByClientId) {
    validateUpload(content, declaredMimeType);

    UUID versionId = UUID.randomUUID();
    int versionNumber = documentVersionRepository.nextVersionNumber(document.id());
    String storageKey =
        DocumentStorageKey.build(
            document.companyId(), document.caseId(), document.id(), versionId, originalFilename);
    String checksum = sha256(content);

    storageClient.upload(storageKey, content, declaredMimeType);
    documentVersionRepository.insertFromClient(
        versionId,
        document.id(),
        versionNumber,
        storageKey,
        originalFilename,
        declaredMimeType,
        content.length,
        checksum,
        uploadedByClientId);
    documentRepository.setCurrentVersionAndStatus(document.id(), versionId, ReviewStatus.PENDING);

    activityPublisher.publish(
        CaseActivityEvent.byClient(
            "document.uploaded",
            document.companyId(),
            document.caseId(),
            uploadedByClientId,
            "Document version " + versionNumber + " uploaded (" + originalFilename + ")"));

    notificationPublisher.notifyUsers(
        document.companyId(),
        NotificationType.DOCUMENT_UPLOADED,
        notificationRecipients.assignedUsers(document.caseId()),
        Map.of(
            "caseId",
            document.caseId(),
            "documentId",
            document.id(),
            "versionNumber",
            versionNumber,
            "filename",
            originalFilename));

    return documentVersionRepository.findById(versionId).orElseThrow();
  }

  private void validateUpload(byte[] content, String mimeType) {
    if (content == null || content.length == 0) {
      throw new ValidationException("EMPTY_FILE", "Uploaded file is empty.");
    }
    if (content.length > storageProperties.maxFileSizeBytes()) {
      throw new ValidationException(
          "FILE_TOO_LARGE",
          "File exceeds the maximum allowed size ("
              + storageProperties.maxFileSizeBytes()
              + " bytes).");
    }
    if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
      throw new ValidationException("UNSUPPORTED_MIME_TYPE", "Unsupported file type: " + mimeType);
    }
  }

  private static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public List<DocumentVersion> listVersions(Document document) {
    return documentVersionRepository.findAllByDocumentId(document.id());
  }

  @Transactional
  public DocumentVersion review(
      Document document, ReviewStatus decision, UUID reviewerId, String comment) {
    if (decision == ReviewStatus.PENDING) {
      throw new ValidationException(
          "INVALID_REVIEW_DECISION", "Review decision must be APPROVED or REJECTED.");
    }
    if (document.currentVersionId() == null) {
      throw new ValidationException(
          "NO_VERSION_TO_REVIEW", "Document has no uploaded version yet.");
    }
    documentVersionRepository.review(document.currentVersionId(), decision, reviewerId, comment);
    documentRepository.updateStatus(document.id(), decision);
    DocumentVersion version =
        documentVersionRepository.findById(document.currentVersionId()).orElseThrow();
    notifyUploaderOfReview(document, decision, version);
    return version;
  }

  /**
   * Notifies whoever uploaded the version under review (user or Portal client) of the decision —
   * the actor/reviewer is never the recipient.
   */
  private void notifyUploaderOfReview(
      Document document, ReviewStatus decision, DocumentVersion version) {
    Map<String, Object> payload =
        Map.of(
            "caseId", document.caseId(),
            "documentId", document.id(),
            "versionNumber", version.versionNumber(),
            "filename", version.originalFilename(),
            "decision", decision.name());
    if (version.uploadedBy() != null) {
      notificationPublisher.notifyUsers(
          document.companyId(),
          NotificationType.DOCUMENT_REVIEWED,
          List.of(version.uploadedBy()),
          payload);
    } else if (version.uploadedByClientId() != null) {
      notificationPublisher.notifyClients(
          document.companyId(),
          NotificationType.DOCUMENT_REVIEWED,
          List.of(version.uploadedByClientId()),
          payload);
    }
  }

  @Transactional
  public DocumentPublication publish(Document document, UUID publishedBy) {
    if (document.currentVersionId() == null) {
      throw new ValidationException(
          "NO_VERSION_TO_PUBLISH", "Document has no uploaded version yet.");
    }
    documentPublicationRepository.revokeActive(document.id());
    UUID id =
        documentPublicationRepository.insert(
            document.companyId(), document.id(), document.currentVersionId(), publishedBy);
    notificationPublisher.notifyClients(
        document.companyId(),
        NotificationType.DOCUMENT_PUBLISHED,
        notificationRecipients.caseClients(document.caseId()),
        Map.of(
            "caseId", document.caseId(),
            "documentId", document.id()));
    return documentPublicationRepository
        .findActiveByDocumentId(document.id())
        .filter(p -> p.id().equals(id))
        .orElseThrow();
  }

  @Transactional
  public void unpublish(Document document) {
    documentPublicationRepository.revokeActive(document.id());
  }

  public URI presignedDownloadUrl(DocumentVersion version) {
    return storageClient.presignedDownloadUrl(version.storageKey(), version.originalFilename());
  }

  public DocumentVersion currentVersionOrThrow(Document document) {
    if (document.currentVersionId() == null) {
      throw new ResourceNotFoundException(
          "DOCUMENT_VERSION_NOT_FOUND", "Document has no uploaded version yet.");
    }
    return documentVersionRepository.findById(document.currentVersionId()).orElseThrow();
  }

  public DocumentVersion versionOfDocumentOrThrow(Document document, UUID versionId) {
    return documentVersionRepository
        .findById(versionId)
        .filter(v -> v.documentId().equals(document.id()))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "DOCUMENT_VERSION_NOT_FOUND", "Document version not found."));
  }
}
