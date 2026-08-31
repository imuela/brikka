package com.brika.platform.financing;

import java.math.BigDecimal;

/**
 * BRIKKA V2 I4. One rate discount attached to a simulation (payroll, home/life insurance, alarm,
 * card, investments, …). Data, not code: the applied logic never hardcodes a fixed set of
 * bonifications — it sums the {@code rate} of every entry whose {@code active} is true and
 * subtracts it from the base rate. {@code code} is a stable machine key (see {@link
 * SimulationBonificationCatalog} for the well-known ones); {@code label} is the display text;
 * {@code rate} is a percentage-point reduction (e.g. 0.30 = 0.30 pp) with {@code numeric(7,4)}
 * precision.
 */
public record SimulationBonification(String code, String label, BigDecimal rate, boolean active) {}
