package com.brika.platform.casemgmt.web;

/**
 * BRIKKA V2 I3: {@code override} (optional, default false) forces the transition past its business
 * preconditions. It additionally requires the CASE_TRANSITION_OVERRIDE permission and a non-blank
 * {@code reason}.
 */
public record ChangeCaseStatusApiRequest(String newStatus, String reason, Boolean override) {

  public ChangeCaseStatusApiRequest(String newStatus, String reason) {
    this(newStatus, reason, null);
  }
}
