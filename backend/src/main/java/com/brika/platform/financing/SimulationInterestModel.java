package com.brika.platform.financing;

import java.math.BigDecimal;
import java.util.List;

/**
 * BRIKKA V2 I4. Validated interest inputs of a simulation, handed to {@link
 * SimulationInterestCalculator}. Which fields are populated depends on {@link #type()}:
 *
 * <ul>
 *   <li>{@code FIXED}: {@code fixedRate}.
 *   <li>{@code VARIABLE}: {@code euriborRate} + {@code spreadRate}.
 *   <li>{@code MIXED}: {@code fixedPeriodMonths} + {@code fixedPeriodRate} for the initial tranche,
 *       plus {@code euriborRate} + {@code spreadRate} for the later variable tranche.
 * </ul>
 *
 * The unused fields are {@code null}. Structural validation (which field is required / not
 * applicable for a type, ranges, fixed period {@literal <} term) happens in {@link
 * SimulationService} before this record is built.
 */
public record SimulationInterestModel(
    SimulationInterestType type,
    BigDecimal principal,
    int termMonths,
    BigDecimal fixedRate,
    BigDecimal euriborRate,
    BigDecimal spreadRate,
    Integer fixedPeriodMonths,
    BigDecimal fixedPeriodRate,
    List<SimulationBonification> bonifications) {}
