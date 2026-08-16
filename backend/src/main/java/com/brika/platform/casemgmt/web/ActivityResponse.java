package com.brika.platform.casemgmt.web;

import com.brika.platform.activity.Activity;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
    UUID id, UUID caseId, String activityType, String summary, Instant createdAt) {

  public static ActivityResponse from(Activity activity) {
    return new ActivityResponse(
        activity.id(),
        activity.caseId(),
        activity.activityType(),
        activity.summary(),
        activity.createdAt());
  }
}
