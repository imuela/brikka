package com.brika.platform.financing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BRIKKA V2 I4. Well-known bonification codes and their default Spanish label — <b>for display and
 * client convenience only</b>. The rate discount calculation ({@link SimulationInterestCalculator})
 * never consults this catalog: it works purely off the {@code rate}/{@code active} fields of the
 * {@link SimulationBonification} entries the caller sends. Callers may also send an unlisted code
 * (e.g. {@code OTHER}) as long as it carries a non-blank label — the model stays extensible without
 * a migration.
 */
public final class SimulationBonificationCatalog {

  private static final Map<String, String> KNOWN = new LinkedHashMap<>();

  static {
    KNOWN.put("PAYROLL", "Domiciliación de nómina");
    KNOWN.put("HOME_INSURANCE", "Seguro de hogar");
    KNOWN.put("LIFE_INSURANCE", "Seguro de vida");
    KNOWN.put("ALARM", "Alarma");
    KNOWN.put("CARD", "Tarjeta");
    KNOWN.put("INVESTMENTS", "Inversiones / plan de pensiones");
    KNOWN.put("OTHER", "Otra bonificación");
  }

  private SimulationBonificationCatalog() {}

  public static Map<String, String> knownLabels() {
    return Map.copyOf(KNOWN);
  }

  public static String defaultLabel(String code) {
    return KNOWN.get(code);
  }
}
