package com.brika.platform.integrations.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.integrations.Integration;
import com.brika.platform.integrations.IntegrationRepository;
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
 * ADR-INTEGRATIONS-001 / 17_API_SPECIFICATION_DETAILED.md §17D: read-only/status in V1, no adapter
 * execution. GLOBAL (no requireTenant() — same pattern as ScoringRulesetController), gated by
 * INTEGRATION_READ (SUPERADMIN only, APPROVED GLOBAL since V9). Deliberately no POST/PATCH/DELETE.
 */
@RestController
public class IntegrationController {

  private final AuthorizationService authorizationService;
  private final IntegrationRepository integrationRepository;
  private final ObjectMapper objectMapper;

  public IntegrationController(
      AuthorizationService authorizationService,
      IntegrationRepository integrationRepository,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.integrationRepository = integrationRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/integrations")
  public List<IntegrationResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "INTEGRATION_READ");
    return integrationRepository.findAll().stream().map(this::toResponse).toList();
  }

  @GetMapping("/api/v1/integrations/{id}")
  public IntegrationResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "INTEGRATION_READ");
    Integration integration =
        integrationRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "Not found."));
    return toResponse(integration);
  }

  private IntegrationResponse toResponse(Integration integration) {
    return new IntegrationResponse(
        integration.id(),
        integration.companyId(),
        integration.type(),
        integration.status(),
        readJson(integration.configJson()),
        integration.createdAt(),
        integration.updatedAt());
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
