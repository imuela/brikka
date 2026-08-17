package com.brika.platform.audit.web;

import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-AUDIT-001 / Sprint 11: read-only in V1, GLOBAL (no requireTenant() — same pattern as
 * ScoringRulesetController/IntegrationController), gated by AUDIT_READ (SUPERADMIN only, APPROVED
 * GLOBAL since V9 — does not require SUPPORT_SESSION, unlike REPORT_READ). No filters, no
 * pagination, no export: none are documented, so none are invented (D11-x, Sprint 11 analysis
 * gate).
 */
@RestController
public class AuditEventController {

  private final AuthorizationService authorizationService;
  private final AuditEventRepository auditEventRepository;
  private final ObjectMapper objectMapper;

  public AuditEventController(
      AuthorizationService authorizationService,
      AuditEventRepository auditEventRepository,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.auditEventRepository = auditEventRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/audit-events")
  public List<AuditEventResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "AUDIT_READ");
    return auditEventRepository.findAll().stream().map(this::toResponse).toList();
  }

  @GetMapping("/api/v1/audit-events/{id}")
  public AuditEventResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "AUDIT_READ");
    AuditEvent event =
        auditEventRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("AUDIT_EVENT_NOT_FOUND", "Not found."));
    return toResponse(event);
  }

  private AuditEventResponse toResponse(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.companyId(),
        event.actorUserId(),
        event.actorClientId(),
        event.action(),
        event.resourceType(),
        event.resourceId(),
        event.requestId(),
        readJson(event.metadataJson()),
        event.createdAt());
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
