package com.brika.platform.portal.web;

import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentPublication;
import com.brika.platform.document.DocumentPublicationRepository;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentRequestRepository;
import com.brika.platform.document.DocumentRequestStatus;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import com.brika.platform.portal.PortalAuthorizationService;
import com.brika.platform.portal.PortalCaseAccessResult;
import com.brika.platform.portal.PortalCaseAccessService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 07_PORTAL_CLIENTE.md §Documentación / §Seguridad: only documents with an active
 * document_publications row are visible — "documentación interna: oculta" by default, exactly the
 * mechanism already built in Sprint 4 (DocumentController.publish/unpublish), reused unchanged
 * here.
 *
 * <p>Upload ("subir"/"sustituir") always creates a brand new Document + first version (mirrors the
 * internal two-step create+upload flow collapsed into one call) — the schema has no way to identify
 * "the" existing document to replace when a case can have several clients/documents of the same
 * type, so inventing a replace-matching heuristic was avoided (Sprint 7 gate review, non-blocking
 * interpretation). "Responder solicitudes" is implemented as a best-effort side effect: if a
 * PENDING document_request exists for this case/type/client, it is opportunistically marked
 * FULFILLED — document_requests has no FK back to the fulfilling document, so this is a heuristic
 * match, not a hard link (same disclosure).
 */
@RestController
public class PortalDocumentController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final PortalCaseAccessService portalCaseAccessService;
  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentPublicationRepository documentPublicationRepository;
  private final DocumentRequestRepository documentRequestRepository;
  private final DocumentService documentService;

  public PortalDocumentController(
      PortalAuthorizationService portalAuthorizationService,
      PortalCaseAccessService portalCaseAccessService,
      DocumentRepository documentRepository,
      DocumentVersionRepository documentVersionRepository,
      DocumentPublicationRepository documentPublicationRepository,
      DocumentRequestRepository documentRequestRepository,
      DocumentService documentService) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.portalCaseAccessService = portalCaseAccessService;
    this.documentRepository = documentRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.documentPublicationRepository = documentPublicationRepository;
    this.documentRequestRepository = documentRequestRepository;
    this.documentService = documentService;
  }

  @GetMapping("/api/v1/portal/cases/{id}/documents")
  public List<PortalDocumentResponse> list(Authentication authentication, @PathVariable UUID id) {
    portalAuthorizationService.requirePermission(authentication, "PORTAL_DOCUMENT_READ");
    var access =
        portalCaseAccessService.requireCaseAccess(authentication, "PORTAL_DOCUMENT_READ", id);

    return documentRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::publishedViewOf)
        .flatMap(Optional::stream)
        .toList();
  }

  @PostMapping("/api/v1/portal/cases/{id}/documents")
  public PortalDocumentResponse upload(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam UUID documentTypeId,
      @RequestParam("file") MultipartFile file) {
    PortalCaseAccessResult access =
        portalCaseAccessService.requireCaseAccess(authentication, "PORTAL_DOCUMENT_UPLOAD", id);

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    Document document =
        documentService.createDocument(access.tenantId(), access.theCase().id(), documentTypeId);
    DocumentVersion version =
        documentService.uploadVersionFromClient(
            document,
            content,
            file.getOriginalFilename(),
            file.getContentType(),
            access.account().clientId());

    documentRequestRepository
        .findPendingByCaseAndTypeAndClient(
            access.theCase().id(), documentTypeId, access.account().clientId())
        .ifPresent(
            request ->
                documentRequestRepository.updateStatus(
                    request.id(), DocumentRequestStatus.FULFILLED));

    return PortalDocumentResponse.from(document, version, null);
  }

  private Optional<PortalDocumentResponse> publishedViewOf(Document document) {
    return documentPublicationRepository
        .findActiveByDocumentId(document.id())
        .map(
            (DocumentPublication publication) -> {
              var version =
                  documentVersionRepository.findById(publication.documentVersionId()).orElseThrow();
              return PortalDocumentResponse.from(document, version, publication.publishedAt());
            });
  }
}
