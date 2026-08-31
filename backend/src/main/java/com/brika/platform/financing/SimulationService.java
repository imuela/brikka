package com.brika.platform.financing;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * BRIKKA V2 I4. Validates the interest structure of a simulation (R18), applies its bonifications
 * to the rate that drives the payment (R19 — Legacy stored them but never applied them), recomputes
 * the payment with {@link MortgagePaymentCalculator} and persists the enriched row. All validation
 * is domain validation via {@link ValidationException} (the codebase has no Bean Validation — see
 * {@code GlobalExceptionHandler}), answered as 400 {@code {code, message, requestId}}.
 */
@Service
public class SimulationService {

  private final SimulationRepository simulationRepository;
  private final ObjectMapper objectMapper;

  public SimulationService(SimulationRepository simulationRepository, ObjectMapper objectMapper) {
    this.simulationRepository = simulationRepository;
    this.objectMapper = objectMapper;
  }

  public Simulation create(
      UUID companyId, UUID caseId, UUID actorUserId, CreateSimulationCommand command) {
    SimulationInterestType type =
        SimulationInterestType.fromValue(command.interestType())
            .orElseThrow(
                () ->
                    new ValidationException(
                        "INVALID_SIMULATION_INTEREST_TYPE",
                        "interestType must be one of FIXED, VARIABLE, MIXED."));

    BigDecimal principal = command.principal();
    if (principal == null || principal.signum() <= 0) {
      throw new ValidationException(
          "INVALID_SIMULATION_AMOUNT", "principal must be a positive amount.");
    }
    Integer termMonths = command.termMonths();
    if (termMonths == null || termMonths < 1) {
      throw new ValidationException("INVALID_SIMULATION_TERM", "termMonths must be at least 1.");
    }

    List<SimulationBonification> bonifications = normalizeBonifications(command.bonifications());
    SimulationInterestModel model =
        buildInterestModel(type, principal, termMonths, command, bonifications);
    SimulationComputation computation = SimulationInterestCalculator.compute(model);

    UUID id =
        simulationRepository.insert(
            companyId,
            caseId,
            actorUserId,
            model,
            computation,
            toJson(bonifications),
            command.icoGuarantee(),
            toJson(command.metadata() == null ? Map.of() : command.metadata()));
    return simulationRepository.findById(id).orElseThrow();
  }

  private SimulationInterestModel buildInterestModel(
      SimulationInterestType type,
      BigDecimal principal,
      int termMonths,
      CreateSimulationCommand command,
      List<SimulationBonification> bonifications) {
    switch (type) {
      case FIXED -> {
        requirePresent("fixedRate", command.fixedRate(), type);
        requireNonNegative("fixedRate", command.fixedRate());
        requireAbsent("euriborRate", command.euriborRate(), type);
        requireAbsent("spreadRate", command.spreadRate(), type);
        requireAbsent("fixedPeriodMonths", command.fixedPeriodMonths(), type);
        requireAbsent("fixedPeriodRate", command.fixedPeriodRate(), type);
        return new SimulationInterestModel(
            type,
            principal,
            termMonths,
            command.fixedRate(),
            null,
            null,
            null,
            null,
            bonifications);
      }
      case VARIABLE -> {
        requirePresent("euriborRate", command.euriborRate(), type);
        requirePresent("spreadRate", command.spreadRate(), type);
        requireNonNegative("spreadRate", command.spreadRate());
        requireAbsent("fixedRate", command.fixedRate(), type);
        requireAbsent("fixedPeriodMonths", command.fixedPeriodMonths(), type);
        requireAbsent("fixedPeriodRate", command.fixedPeriodRate(), type);
        return new SimulationInterestModel(
            type,
            principal,
            termMonths,
            null,
            command.euriborRate(),
            command.spreadRate(),
            null,
            null,
            bonifications);
      }
      case MIXED -> {
        requirePresent("fixedPeriodMonths", command.fixedPeriodMonths(), type);
        requirePresent("fixedPeriodRate", command.fixedPeriodRate(), type);
        requirePresent("euriborRate", command.euriborRate(), type);
        requirePresent("spreadRate", command.spreadRate(), type);
        requireNonNegative("fixedPeriodRate", command.fixedPeriodRate());
        requireNonNegative("spreadRate", command.spreadRate());
        requireAbsent("fixedRate", command.fixedRate(), type);
        int fixedPeriodMonths = command.fixedPeriodMonths();
        if (fixedPeriodMonths < 1 || fixedPeriodMonths >= termMonths) {
          throw new ValidationException(
              "INVALID_SIMULATION_FIXED_PERIOD",
              "fixedPeriodMonths must be between 1 and termMonths - 1 (the fixed tranche cannot"
                  + " cover the whole term).");
        }
        return new SimulationInterestModel(
            type,
            principal,
            termMonths,
            null,
            command.euriborRate(),
            command.spreadRate(),
            fixedPeriodMonths,
            command.fixedPeriodRate(),
            bonifications);
      }
      default ->
          throw new ValidationException(
              "INVALID_SIMULATION_INTEREST_TYPE", "Unsupported interest type.");
    }
  }

  /**
   * Re-derives the full computation of a stored simulation (base/final rate, payment, and for MIXED
   * the variable-tranche balance and payment). Every input is persisted, so this is deterministic
   * and reproduces the stored {@code final_interest_rate} / {@code estimated_payment}. Used to
   * expose the MIXED variable phase without persisting derived values.
   */
  public SimulationComputation computationOf(Simulation simulation) {
    SimulationInterestType type =
        SimulationInterestType.fromValue(simulation.interestType())
            .orElse(SimulationInterestType.FIXED);
    SimulationInterestModel model =
        new SimulationInterestModel(
            type,
            simulation.principal(),
            simulation.termMonths(),
            type == SimulationInterestType.FIXED ? simulation.baseInterestRate() : null,
            simulation.euriborRate(),
            simulation.spreadRate(),
            simulation.fixedPeriodMonths(),
            simulation.fixedPeriodRate(),
            parseBonifications(simulation.bonifications()));
    return SimulationInterestCalculator.compute(model);
  }

  public List<SimulationBonification> parseBonifications(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<SimulationBonification>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private List<SimulationBonification> normalizeBonifications(List<SimulationBonification> input) {
    List<SimulationBonification> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    Set<String> seenCodes = new HashSet<>();
    for (SimulationBonification bonification : input) {
      String code = bonification.code() == null ? "" : bonification.code().trim();
      if (code.isEmpty() || code.length() > 64) {
        throw new ValidationException(
            "INVALID_SIMULATION_BONIFICATION",
            "Each bonification needs a non-blank code (<= 64 chars).");
      }
      if (!seenCodes.add(code)) {
        throw new ValidationException(
            "INVALID_SIMULATION_BONIFICATION", "Duplicate bonification code \"" + code + "\".");
      }
      if (bonification.rate() == null || bonification.rate().signum() < 0) {
        throw new ValidationException(
            "INVALID_SIMULATION_BONIFICATION",
            "Bonification \"" + code + "\" needs a rate of zero or more.");
      }
      String label =
          bonification.label() == null || bonification.label().isBlank()
              ? SimulationBonificationCatalog.defaultLabel(code)
              : bonification.label().trim();
      if (label == null || label.isBlank()) {
        throw new ValidationException(
            "INVALID_SIMULATION_BONIFICATION",
            "Bonification \"" + code + "\" needs a label (it is not a well-known code).");
      }
      result.add(
          new SimulationBonification(code, label, bonification.rate(), bonification.active()));
    }
    return result;
  }

  private void requirePresent(String field, Object value, SimulationInterestType type) {
    if (value == null) {
      throw new ValidationException(
          "SIMULATION_INTEREST_MODEL_MISMATCH",
          field + " is required for a " + type + " simulation.");
    }
  }

  private void requireAbsent(String field, Object value, SimulationInterestType type) {
    if (value != null) {
      throw new ValidationException(
          "SIMULATION_INTEREST_MODEL_MISMATCH",
          field + " is not applicable to a " + type + " simulation.");
    }
  }

  private void requireNonNegative(String field, BigDecimal value) {
    if (value != null && value.signum() < 0) {
      throw new ValidationException("NEGATIVE_SIMULATION_VALUE", field + " cannot be negative.");
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_JSON", "Value could not be serialized.");
    }
  }
}
