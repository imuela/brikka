package com.brika.platform.audit.web;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID companyId,
    UUID actorUserId,
    UUID actorClientId,
    String action,
    String resourceType,
    UUID resourceId,
    String requestId,
    Object metadata,
    Instant createdAt) {}
