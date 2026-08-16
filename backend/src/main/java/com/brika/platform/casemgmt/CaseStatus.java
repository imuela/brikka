package com.brika.platform.casemgmt;

/** 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §2. COMPLETED/CANCELLED are terminal. */
public enum CaseStatus {
  PRESTUDY,
  DOCUMENTATION,
  ANALYSIS,
  BANK_SEARCH,
  BANK_SUBMISSION,
  BANK_REVIEW,
  OFFER,
  FORMALIZATION,
  COMPLETED,
  CANCELLED;

  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED;
  }
}
