package com.brika.platform.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-AUDIT-001: security/compliance log, distinct from {@code activities} (functional timeline,
 * Sprint 3). Immutable — no update/delete. 1:1 with the {@code audit_events} columns (V1); no field
 * added beyond what the table already has (Sprint 11 adenda: no {@code support_session_id}, since
 * SUPPORT_SESSION is deferred).
 */
public record AuditEvent(
    UUID id,
    UUID companyId,
    UUID actorUserId,
    UUID actorClientId,
    String action,
    String resourceType,
    UUID resourceId,
    String requestId,
    String metadataJson,
    Instant createdAt) {}
