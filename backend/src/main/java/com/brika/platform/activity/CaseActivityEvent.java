package com.brika.platform.activity;

import java.util.UUID;

/**
 * Functional contract for a CASE domain event, independent of how it is delivered
 * (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §8, ADR-AUDIT-001). activityType uses the exact event
 * names from the workflow spec (CaseCreated, CaseStatusChanged, CaseCancelled, CaseCompleted,
 * CaseReopened). Exactly one of actorUserId / actorClientId is set (mirrors
 * activities.actor_user_id / activities.actor_client_id, both nullable since V7) — a Portal
 * Cliente-triggered event (Sprint 7) has no users row to attribute it to.
 */
public record CaseActivityEvent(
    String activityType,
    UUID companyId,
    UUID caseId,
    UUID actorUserId,
    UUID actorClientId,
    String summary) {

  public static CaseActivityEvent byUser(
      String activityType, UUID companyId, UUID caseId, UUID actorUserId, String summary) {
    return new CaseActivityEvent(activityType, companyId, caseId, actorUserId, null, summary);
  }

  public static CaseActivityEvent byClient(
      String activityType, UUID companyId, UUID caseId, UUID actorClientId, String summary) {
    return new CaseActivityEvent(activityType, companyId, caseId, null, actorClientId, summary);
  }
}
