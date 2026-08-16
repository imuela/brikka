package com.brika.platform.casemgmt;

/** 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §6, catálogo cerrado de motivos iniciales. */
public enum CancellationReason {
  CLIENT_REQUEST,
  INELIGIBLE,
  NO_FINANCING,
  PROPERTY_ISSUE,
  DUPLICATE,
  ABANDONED,
  OTHER
}
