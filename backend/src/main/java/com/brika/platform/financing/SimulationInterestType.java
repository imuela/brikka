package com.brika.platform.financing;

import java.util.Arrays;
import java.util.Optional;

/**
 * BRIKKA V2 I4. Interest structure of a mortgage simulation (business rule R18):
 *
 * <ul>
 *   <li>{@code FIXED} — a single fixed annual rate for the whole term.
 *   <li>{@code VARIABLE} — Euribor + spread, revised over the term. The applicable rate for the
 *       payment estimate is {@code euribor + spread}.
 *   <li>{@code MIXED} — an initial fixed tranche of {@code fixedPeriodMonths} months at {@code
 *       fixedPeriodRate}, then a variable tranche at {@code euribor + spread} for the rest.
 * </ul>
 *
 * Bonifications (R19) reduce whichever rate is in effect. Legacy stored the breakdown columns but
 * never applied the bonifications; BRIKKA V2 applies them for real.
 */
public enum SimulationInterestType {
  FIXED,
  VARIABLE,
  MIXED;

  public static Optional<SimulationInterestType> fromValue(String value) {
    if (value == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(t -> t.name().equals(value)).findFirst();
  }
}
