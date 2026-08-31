package com.brika.platform.financing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BRIKKA V2 I4. Service-layer input for creating a simulation, decoupled from the web DTO. Only the
 * fields relevant to {@code interestType} need to be set (see {@link SimulationInterestType}); the
 * rest are {@code null}. {@link SimulationService} validates the combination before computing.
 */
public record CreateSimulationCommand(
    String interestType,
    BigDecimal principal,
    Integer termMonths,
    BigDecimal fixedRate,
    BigDecimal euriborRate,
    BigDecimal spreadRate,
    Integer fixedPeriodMonths,
    BigDecimal fixedPeriodRate,
    List<SimulationBonification> bonifications,
    boolean icoGuarantee,
    Map<String, Object> metadata) {}
