package com.brika.platform.financing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * BRIKKA V2 I4. Domain validation of the simulation interest model — every rejection is a {@link
 * ValidationException} with a stable code (answered as 400 {@code {code,message,requestId}}). The
 * happy-path calculations are covered by {@link SimulationInterestCalculatorTest} and the wiring by
 * {@code FinancingEndpointsIT}.
 */
class SimulationServiceValidationTest {

  private final SimulationService service =
      new SimulationService(mock(SimulationRepository.class), new ObjectMapper());

  private CreateSimulationCommand base(String type) {
    return new CreateSimulationCommand(
        type,
        new BigDecimal("200000"),
        300,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        false,
        Map.of());
  }

  private void expect(String code, CreateSimulationCommand command) {
    assertThatThrownBy(
            () -> service.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), command))
        .isInstanceOf(ValidationException.class)
        .satisfies(e -> assertThat(((ValidationException) e).code()).isEqualTo(code));
  }

  @Test
  void unknownInterestTypeIsRejected() {
    expect("INVALID_SIMULATION_INTEREST_TYPE", base("BALLOON"));
  }

  @Test
  void nonPositivePrincipalIsRejected() {
    expect(
        "INVALID_SIMULATION_AMOUNT",
        new CreateSimulationCommand(
            "FIXED",
            BigDecimal.ZERO,
            300,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void termBelowOneIsRejected() {
    expect(
        "INVALID_SIMULATION_TERM",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            0,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void fixedWithoutRateIsRejected() {
    expect("SIMULATION_INTEREST_MODEL_MISMATCH", base("FIXED"));
  }

  @Test
  void fixedCarryingEuriborIsRejected() {
    expect(
        "SIMULATION_INTEREST_MODEL_MISMATCH",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            300,
            new BigDecimal("3.0"),
            new BigDecimal("2.0"),
            null,
            null,
            null,
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void variableWithoutSpreadIsRejected() {
    expect(
        "SIMULATION_INTEREST_MODEL_MISMATCH",
        new CreateSimulationCommand(
            "VARIABLE",
            new BigDecimal("200000"),
            300,
            null,
            new BigDecimal("2.0"),
            null,
            null,
            null,
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void variableWithNegativeSpreadIsRejected() {
    expect(
        "NEGATIVE_SIMULATION_VALUE",
        new CreateSimulationCommand(
            "VARIABLE",
            new BigDecimal("200000"),
            300,
            null,
            new BigDecimal("2.0"),
            new BigDecimal("-0.10"),
            null,
            null,
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void mixedFixedPeriodCoveringWholeTermIsRejected() {
    expect(
        "INVALID_SIMULATION_FIXED_PERIOD",
        new CreateSimulationCommand(
            "MIXED",
            new BigDecimal("200000"),
            300,
            null,
            new BigDecimal("2.0"),
            new BigDecimal("0.80"),
            300,
            new BigDecimal("2.5"),
            List.of(),
            false,
            Map.of()));
  }

  @Test
  void duplicateBonificationCodeIsRejected() {
    expect(
        "INVALID_SIMULATION_BONIFICATION",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            300,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(
                new SimulationBonification("PAYROLL", "Nómina", new BigDecimal("0.20"), true),
                new SimulationBonification("PAYROLL", "Nómina", new BigDecimal("0.10"), true)),
            false,
            Map.of()));
  }

  @Test
  void bonificationWithNegativeRateIsRejected() {
    expect(
        "INVALID_SIMULATION_BONIFICATION",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            300,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(new SimulationBonification("PAYROLL", "Nómina", new BigDecimal("-0.20"), true)),
            false,
            Map.of()));
  }

  @Test
  void bonificationWithBlankCodeIsRejected() {
    expect(
        "INVALID_SIMULATION_BONIFICATION",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            300,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(new SimulationBonification("  ", "x", new BigDecimal("0.20"), true)),
            false,
            Map.of()));
  }

  @Test
  void unknownBonificationCodeWithoutLabelIsRejected() {
    expect(
        "INVALID_SIMULATION_BONIFICATION",
        new CreateSimulationCommand(
            "FIXED",
            new BigDecimal("200000"),
            300,
            new BigDecimal("3.0"),
            null,
            null,
            null,
            null,
            List.of(new SimulationBonification("CUSTOM_X", null, new BigDecimal("0.20"), true)),
            false,
            Map.of()));
  }
}
