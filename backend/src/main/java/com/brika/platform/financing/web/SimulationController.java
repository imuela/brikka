package com.brika.platform.financing.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.financing.CreateSimulationCommand;
import com.brika.platform.financing.Simulation;
import com.brika.platform.financing.SimulationBonification;
import com.brika.platform.financing.SimulationComputation;
import com.brika.platform.financing.SimulationInterestType;
import com.brika.platform.financing.SimulationRepository;
import com.brika.platform.financing.SimulationService;
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
 * (Sprint 5 pre-flight decision). BRIKKA V2 I4: the interest structure + bonifications are
 * validated and the payment recomputed in {@link SimulationService}; this controller only maps
 * DTOs.
 */
@RestController
public class SimulationController {

  private final CaseAccessService caseAccessService;
  private final SimulationService simulationService;
  private final SimulationRepository simulationRepository;
  private final ObjectMapper objectMapper;

  public SimulationController(
      CaseAccessService caseAccessService,
      SimulationService simulationService,
      SimulationRepository simulationRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.simulationService = simulationService;
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
    Simulation simulation =
        simulationService.create(
            access.tenantId(), access.theCase().id(), access.user().id(), toCommand(request));
    return toResponse(simulation);
  }

  private CreateSimulationCommand toCommand(CreateSimulationApiRequest request) {
    List<SimulationBonification> bonifications =
        request.bonifications() == null
            ? List.of()
            : request.bonifications().stream()
                .map(
                    input ->
                        new SimulationBonification(
                            input.code(),
                            input.label(),
                            input.rate(),
                            Boolean.TRUE.equals(input.active())))
                .toList();
    return new CreateSimulationCommand(
        request.interestType(),
        request.principal(),
        request.termMonths(),
        request.fixedRate(),
        request.euriborRate(),
        request.spreadRate(),
        request.fixedPeriodMonths(),
        request.fixedPeriodRate(),
        bonifications,
        Boolean.TRUE.equals(request.icoGuarantee()),
        request.metadata());
  }

  private SimulationResponse toResponse(Simulation simulation) {
    List<SimulationResponse.Bonification> bonifications =
        simulationService.parseBonifications(simulation.bonifications()).stream()
            .map(
                b -> new SimulationResponse.Bonification(b.code(), b.label(), b.rate(), b.active()))
            .toList();

    SimulationResponse.VariablePhase variablePhase = null;
    if (SimulationInterestType.MIXED.name().equals(simulation.interestType())) {
      SimulationComputation computation = simulationService.computationOf(simulation);
      variablePhase =
          new SimulationResponse.VariablePhase(
              computation.variablePhaseBaseRate(),
              computation.variablePhaseFinalRate(),
              computation.outstandingBalanceAtSwitch(),
              computation.variablePhaseEstimatedPayment());
    }

    return new SimulationResponse(
        simulation.id(),
        simulation.caseId(),
        simulation.principal(),
        simulation.interestRate(),
        simulation.termMonths(),
        simulation.estimatedPayment(),
        simulation.interestType(),
        simulation.baseInterestRate(),
        simulation.finalInterestRate(),
        simulation.euriborRate(),
        simulation.spreadRate(),
        simulation.fixedPeriodMonths(),
        simulation.fixedPeriodRate(),
        simulation.icoGuarantee(),
        bonifications,
        variablePhase,
        readJson(simulation.metadata()),
        simulation.createdBy(),
        simulation.createdAt());
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
