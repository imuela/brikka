package com.brika.platform.document.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.DocumentRequirement;
import com.brika.platform.document.DocumentRequirementRepository;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §9B. Global catalog (ADR-DOC-001): GLOBAL scope for every
 * internal role in ADR-RBAC-001, so this controller never calls requireTenant — unlike every other
 * Sprint 2-4 endpoint, SUPERADMIN can use this one without SUPPORT_SESSION, exactly as the matrix
 * intends (a platform-level catalog, not tenant-owned data). No rows are seeded (Sprint 4
 * pre-flight review): no approved document defines which document_type is mandatory for which
 * operation_type.
 */
@RestController
@RequestMapping("/api/v1/document-requirements")
public class DocumentRequirementController {

  private final AuthorizationService authorizationService;
  private final DocumentRequirementRepository documentRequirementRepository;
  private final ObjectMapper objectMapper;

  public DocumentRequirementController(
      AuthorizationService authorizationService,
      DocumentRequirementRepository documentRequirementRepository,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.documentRequirementRepository = documentRequirementRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public List<DocumentRequirementResponse> list(
      Authentication authentication, @RequestParam(required = false) String operationType) {
    authorizationService.requirePermission(authentication, "DOCUMENT_REQUIREMENT_READ");
    return documentRequirementRepository.findAll(operationType).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/{id}")
  public DocumentRequirementResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "DOCUMENT_REQUIREMENT_READ");
    return toResponse(requireById(id));
  }

  @PostMapping
  public DocumentRequirementResponse create(
      Authentication authentication, @RequestBody CreateDocumentRequirementApiRequest request) {
    authorizationService.requirePermission(authentication, "DOCUMENT_REQUIREMENT_MANAGE");
    UUID id =
        documentRequirementRepository.insert(
            request.operationType(),
            request.documentTypeId(),
            request.mandatory(),
            writeJson(request.conditions()));
    return toResponse(requireById(id));
  }

  @PatchMapping("/{id}")
  public DocumentRequirementResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateDocumentRequirementApiRequest request) {
    authorizationService.requirePermission(authentication, "DOCUMENT_REQUIREMENT_MANAGE");
    requireById(id);
    documentRequirementRepository.update(
        id, request.mandatory(), request.active(), writeJson(request.conditions()));
    return toResponse(requireById(id));
  }

  private DocumentRequirement requireById(UUID id) {
    return documentRequirementRepository
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("DOCUMENT_REQUIREMENT_NOT_FOUND", "Not found."));
  }

  private DocumentRequirementResponse toResponse(DocumentRequirement requirement) {
    return new DocumentRequirementResponse(
        requirement.id(),
        requirement.operationType(),
        requirement.documentTypeId(),
        requirement.mandatory(),
        readJson(requirement.conditions()),
        requirement.active());
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_CONDITIONS", "Conditions could not be serialized.");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(String json) {
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
