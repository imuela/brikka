package com.brika.platform.document.web;

import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 gap fix (approved): document_types is a global read-only catalog, already seeded
 * (V2__seed_system_catalogs.sql) and already served by DocumentTypeRepository, but no controller
 * exposed it — the frontend has no other way to resolve a documentTypeId to a human name. Reuses
 * DOCUMENT_REQUIREMENT_READ (same GLOBAL scope, same catalog-reading intent, no new permission
 * introduced) rather than inventing a new permission for a one-endpoint read-only catalog.
 */
@RestController
@RequestMapping("/api/v1/document-types")
public class DocumentTypeController {

  private final AuthorizationService authorizationService;
  private final DocumentTypeRepository documentTypeRepository;

  public DocumentTypeController(
      AuthorizationService authorizationService, DocumentTypeRepository documentTypeRepository) {
    this.authorizationService = authorizationService;
    this.documentTypeRepository = documentTypeRepository;
  }

  @GetMapping
  public List<DocumentTypeResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "DOCUMENT_REQUIREMENT_READ");
    return documentTypeRepository.findAll().stream().map(DocumentTypeResponse::from).toList();
  }
}
