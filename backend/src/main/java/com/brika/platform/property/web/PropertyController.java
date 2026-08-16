package com.brika.platform.property.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.property.Property;
import com.brika.platform.property.PropertyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §8. PUT is idempotent create-or-replace (CASE 1 ── 0..1
 * PROPERTY, unique per case). Both actions require PROPERTY_UPDATE: PROPERTY_CREATE and
 * PROPERTY_UPDATE share the exact same role/scope assignment in ADR-RBAC-001, so a single
 * permission check is behaviorally equivalent — see Sprint 4 gate review.
 */
@RestController
@RequestMapping("/api/v1/cases/{caseId}/property")
public class PropertyController {

  private final CaseAccessService caseAccessService;
  private final PropertyRepository propertyRepository;
  private final ObjectMapper objectMapper;

  public PropertyController(
      CaseAccessService caseAccessService,
      PropertyRepository propertyRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.propertyRepository = propertyRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public PropertyResponse get(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "PROPERTY_READ", caseId);
    Property property =
        propertyRepository
            .findByCaseId(access.theCase().id())
            .orElseThrow(
                () -> new ResourceNotFoundException("PROPERTY_NOT_FOUND", "Property not found."));
    return toResponse(property);
  }

  @PutMapping
  public PropertyResponse upsert(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody UpsertPropertyApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "PROPERTY_UPDATE", caseId);
    Property property =
        propertyRepository.upsert(
            access.tenantId(),
            access.theCase().id(),
            writeJson(request.address()),
            request.propertyType(),
            request.valuation(),
            request.purchasePrice());
    return toResponse(property);
  }

  private PropertyResponse toResponse(Property property) {
    return new PropertyResponse(
        property.id(),
        property.companyId(),
        property.caseId(),
        readJson(property.address()),
        property.propertyType(),
        property.valuation(),
        property.purchasePrice());
  }

  private String writeJson(Map<String, Object> address) {
    try {
      return objectMapper.writeValueAsString(address == null ? Map.of() : address);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_ADDRESS", "Address could not be serialized.");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(String addressJson) {
    try {
      return objectMapper.readValue(addressJson, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
