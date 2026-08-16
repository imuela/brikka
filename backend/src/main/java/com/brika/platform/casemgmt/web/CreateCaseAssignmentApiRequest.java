package com.brika.platform.casemgmt.web;

import java.util.UUID;

/** assignmentType is free text: no catalog is documented anywhere (Sprint 3 pre-flight review). */
public record CreateCaseAssignmentApiRequest(UUID userId, String assignmentType) {}
