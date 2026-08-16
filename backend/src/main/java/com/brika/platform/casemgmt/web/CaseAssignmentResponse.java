package com.brika.platform.casemgmt.web;

import com.brika.platform.casemgmt.CaseAssignment;
import java.util.UUID;

public record CaseAssignmentResponse(
    UUID id, UUID caseId, UUID userId, String assignmentType, boolean active) {

  public static CaseAssignmentResponse from(CaseAssignment assignment) {
    return new CaseAssignmentResponse(
        assignment.id(),
        assignment.caseId(),
        assignment.userId(),
        assignment.assignmentType(),
        assignment.active());
  }
}
