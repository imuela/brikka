package com.brika.platform.activity;

import java.time.Instant;
import java.util.UUID;

public record Activity(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID actorUserId,
    String activityType,
    String summary,
    Instant createdAt) {}
