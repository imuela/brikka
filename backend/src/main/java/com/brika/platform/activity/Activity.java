package com.brika.platform.activity;

import java.time.Instant;
import java.util.UUID;

/** Exactly one of actorUserId / actorClientId is set — the latter for Portal-triggered events. */
public record Activity(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID actorUserId,
    UUID actorClientId,
    String activityType,
    String summary,
    Instant createdAt) {}
