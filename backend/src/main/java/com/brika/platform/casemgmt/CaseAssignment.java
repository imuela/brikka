package com.brika.platform.casemgmt;

import java.util.UUID;

public record CaseAssignment(
    UUID id, UUID companyId, UUID caseId, UUID userId, String assignmentType, boolean active) {}
