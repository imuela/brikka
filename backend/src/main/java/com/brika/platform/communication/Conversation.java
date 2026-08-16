package com.brika.platform.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * FUNCTIONAL_SPECIFICATION.md §14: type is one of CLIENT/INTERNAL/SYSTEM (chk_conversations_type).
 * Sprint 7 (D2) only ever creates type CLIENT — INTERNAL/SYSTEM are Sprint 8 scope, structurally
 * possible in the schema but never produced by any endpoint in this sprint.
 */
public record Conversation(
    UUID id,
    UUID companyId,
    UUID caseId,
    String type,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
