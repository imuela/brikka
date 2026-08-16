package com.brika.platform.financing.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.financing.Simulation;
import com.brika.platform.financing.SimulationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §11: only list + create are documented for simulations — no
 * single-get, update, or delete. TENANT + ROLE/PERMISSION + CASE ASSIGNMENT via CaseAccessService
 * (Sprint 5 pre-flight decision).
 */
@RestController
public class SimulationController {

  private final CaseAccessService caseAccessService;
  private final SimulationRepository simulationRepository;
  private final ObjectMapper objectMapper;

  public SimulationController(
      CaseAccessService caseAccessService,
      SimulationRepository simulationRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.simulationRepository = simulationRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/cases/{caseId}/simulations")
  public List<SimulationResponse> list(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "SIMULATION_READ", caseId);
    return simulationRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/simulations")
  public SimulationResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateSimulationApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "SIMULATION_CREATE", caseId);
    UUID id =
        simulationRepository.insert(
            access.tenantId(),
            access.theCase().id(),
            request.principal(),
            request.interestRate(),
            request.termMonths(),
            request.estimatedPayment(),
            writeJson(request.metadata()),
            access.user().id());
    return toResponse(simulationRepository.findById(id).orElseThrow());
  }

  private SimulationResponse toResponse(Simulation simulation) {
    return new SimulationResponse(
        simulation.id(),
        simulation.caseId(),
        simulation.principal(),
        simulation.interestRate(),
        simulation.termMonths(),
        simulation.estimatedPayment(),
        readJson(simulation.metadata()),
        simulation.createdBy(),
        simulation.createdAt());
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_JSON", "Value could not be serialized.");
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
