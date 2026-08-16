package com.brika.platform.document.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentAccessResult;
import com.brika.platform.document.DocumentAccessService;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.ReviewStatus;
import com.brika.platform.storage.StorageProperties;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 17_API_SPECIFICATION_DETAILED.md §9. Download endpoints (Sprint 4 pre-flight, decisión aprobada —
 * Opción 3): both the current-version shortcut and a specific historical version, with identical
 * authorization (tenant -> case -> document -> DOCUMENT_DOWNLOAD), always via a short-lived
 * presigned URL, never the raw storage key or credentials.
 */
@RestController
public class DocumentController {

  private final CaseAccessService caseAccessService;
  private final DocumentAccessService documentAccessService;
  private final DocumentRepository documentRepository;
  private final DocumentService documentService;
  private final StorageProperties storageProperties;

  public DocumentController(
      CaseAccessService caseAccessService,
      DocumentAccessService documentAccessService,
      DocumentRepository documentRepository,
      DocumentService documentService,
      StorageProperties storageProperties) {
    this.caseAccessService = caseAccessService;
    this.documentAccessService = documentAccessService;
    this.documentRepository = documentRepository;
    this.documentService = documentService;
    this.storageProperties = storageProperties;
  }

  @GetMapping("/api/v1/cases/{caseId}/documents")
  public List<DocumentResponse> list(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_READ", caseId);
    return documentRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(DocumentResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/documents")
  public DocumentResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateDocumentApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "DOCUMENT_CREATE", caseId);
    Document created =
        documentService.createDocument(
            access.tenantId(), access.theCase().id(), request.documentTypeId());
    return DocumentResponse.from(created);
  }

  @GetMapping("/api/v1/documents/{id}")
  public DocumentResponse get(Authentication authentication, @PathVariable UUID id) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_READ", id);
    return DocumentResponse.from(access.document());
  }

  @GetMapping("/api/v1/documents/{id}/versions")
  public List<DocumentVersionResponse> listVersions(
      Authentication authentication, @PathVariable UUID id) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_READ", id);
    return documentService.listVersions(access.document()).stream()
        .map(DocumentVersionResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/documents/{id}/versions")
  public DocumentVersionResponse uploadVersion(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_UPLOAD", id);
    byte[] content = readBytes(file);
    DocumentVersion version =
        documentService.uploadVersion(
            access.document(),
            content,
            file.getOriginalFilename(),
            file.getContentType(),
            access.user().id());
    return DocumentVersionResponse.from(version);
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ValidationException("UPLOAD_FAILED", "Could not read uploaded file.");
    }
  }

  @PostMapping("/api/v1/documents/{id}/review")
  public DocumentVersionResponse review(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody ReviewDocumentApiRequest request) {
    ReviewStatus decision = parseDecision(request.decision());
    String permission = decision == ReviewStatus.APPROVED ? "DOCUMENT_APPROVE" : "DOCUMENT_REJECT";
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, permission, id);
    DocumentVersion reviewed =
        documentService.review(access.document(), decision, access.user().id(), request.comment());
    return DocumentVersionResponse.from(reviewed);
  }

  private ReviewStatus parseDecision(String value) {
    ReviewStatus decision;
    try {
      decision = ReviewStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("INVALID_REVIEW_DECISION", "Unknown review decision: " + value);
    }
    if (decision == ReviewStatus.PENDING) {
      throw new ValidationException(
          "INVALID_REVIEW_DECISION", "Review decision must be APPROVED or REJECTED.");
    }
    return decision;
  }

  @PostMapping("/api/v1/documents/{id}/publish")
  public DocumentPublicationResponse publish(Authentication authentication, @PathVariable UUID id) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_PUBLISH", id);
    return DocumentPublicationResponse.from(
        documentService.publish(access.document(), access.user().id()));
  }

  @PostMapping("/api/v1/documents/{id}/unpublish")
  public void unpublish(Authentication authentication, @PathVariable UUID id) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_UNPUBLISH", id);
    documentService.unpublish(access.document());
  }

  @GetMapping("/api/v1/documents/{id}/download")
  public DownloadUrlResponse downloadCurrent(Authentication authentication, @PathVariable UUID id) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_DOWNLOAD", id);
    DocumentVersion version = documentService.currentVersionOrThrow(access.document());
    return toDownloadResponse(version);
  }

  @GetMapping("/api/v1/documents/{id}/versions/{versionId}/download")
  public DownloadUrlResponse downloadVersion(
      Authentication authentication, @PathVariable UUID id, @PathVariable UUID versionId) {
    DocumentAccessResult access =
        documentAccessService.requireDocumentAccess(authentication, "DOCUMENT_DOWNLOAD", id);
    DocumentVersion version =
        documentService.versionOfDocumentOrThrow(access.document(), versionId);
    return toDownloadResponse(version);
  }

  private DownloadUrlResponse toDownloadResponse(DocumentVersion version) {
    URI url = documentService.presignedDownloadUrl(version);
    return new DownloadUrlResponse(url.toString(), storageProperties.presignedUrlTtlSeconds());
  }
}
