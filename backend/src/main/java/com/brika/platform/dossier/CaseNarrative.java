package com.brika.platform.dossier;

import java.util.List;

/**
 * BRIKKA V2 I5. Deterministic, structured narrative of a case — a summary, not a field dump, built
 * <b>exclusively from data already stored in Brikka</b> (case, holders, financial profile,
 * property, financing, scoring, viability/DTI, checklist, simulations, fees, case status). No AI,
 * no external dependency, no inference: the same stored data always produces the exact same
 * narrative. It never adds bank recommendations or new financial conclusions.
 */
public record CaseNarrative(List<NarrativeSection> sections) {}
