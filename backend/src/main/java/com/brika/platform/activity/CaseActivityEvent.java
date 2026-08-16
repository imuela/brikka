package com.brika.platform.activity;

import java.util.UUID;

/**
 * Functional contract for a CASE domain event, independent of how it is delivered
 * (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §8, ADR-AUDIT-001). activityType uses the exact event
 * names from the workflow spec (CaseCreated, CaseStatusChanged, CaseCancelled, CaseCompleted,
 * CaseReopened).
 */
public record CaseActivityEvent(
    String activityType, UUID companyId, UUID caseId, UUID actorUserId, String summary) {}
