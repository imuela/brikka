package com.brika.platform.casemgmt;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure state machine, 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §3. Terminal states (COMPLETED,
 * CANCELLED) allow no normal transition — reopening is a separate, explicitly-audited action (§9),
 * not part of this graph.
 */
public final class CaseWorkflow {

  private static final Map<CaseStatus, Set<CaseStatus>> TRANSITIONS = buildTransitions();

  private CaseWorkflow() {}

  private static Map<CaseStatus, Set<CaseStatus>> buildTransitions() {
    Map<CaseStatus, Set<CaseStatus>> transitions = new EnumMap<>(CaseStatus.class);
    transitions.put(
        CaseStatus.PRESTUDY, EnumSet.of(CaseStatus.DOCUMENTATION, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.DOCUMENTATION,
        EnumSet.of(CaseStatus.ANALYSIS, CaseStatus.PRESTUDY, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.ANALYSIS,
        EnumSet.of(CaseStatus.DOCUMENTATION, CaseStatus.BANK_SEARCH, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.BANK_SEARCH,
        EnumSet.of(CaseStatus.ANALYSIS, CaseStatus.BANK_SUBMISSION, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.BANK_SUBMISSION,
        EnumSet.of(CaseStatus.BANK_REVIEW, CaseStatus.BANK_SEARCH, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.BANK_REVIEW,
        EnumSet.of(
            CaseStatus.BANK_SUBMISSION,
            CaseStatus.BANK_SEARCH,
            CaseStatus.OFFER,
            CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.OFFER,
        EnumSet.of(CaseStatus.BANK_REVIEW, CaseStatus.FORMALIZATION, CaseStatus.CANCELLED));
    transitions.put(
        CaseStatus.FORMALIZATION,
        EnumSet.of(CaseStatus.COMPLETED, CaseStatus.OFFER, CaseStatus.CANCELLED));
    transitions.put(CaseStatus.COMPLETED, EnumSet.noneOf(CaseStatus.class));
    transitions.put(CaseStatus.CANCELLED, EnumSet.noneOf(CaseStatus.class));
    return transitions;
  }

  public static boolean canTransition(CaseStatus from, CaseStatus to) {
    return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }
}
