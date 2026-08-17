package com.brika.platform.task;

import java.time.Instant;
import java.util.UUID;

/**
 * 25_CLAUDE_CODE_EXECUTION_GUIDE.md Sprint 8. caseId/assignedTo are both nullable in schema (V1) —
 * a task may exist independent of any case. When caseId is set, access is gated by
 * CaseAccessService (CASE ASSIGNMENT applies); when null, access is tenant-only (same pattern as
 * ClientController).
 */
public record Task(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID assignedTo,
    String type,
    String title,
    String description,
    String status,
    Instant dueAt,
    UUID createdBy,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt) {}
