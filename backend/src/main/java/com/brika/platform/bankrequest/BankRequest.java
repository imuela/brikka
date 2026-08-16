package com.brika.platform.bankrequest;

import java.time.Instant;
import java.util.UUID;

/**
 * 06_BANK_ENGINE_SPECIFICATION.md §6-7: conserves the BANK_CONTACT used, with a snapshot so later
 * edits to the contact never silently alter historical requests. Sprint 6A scope only (approved
 * D1/D2): no matching engine fields, no override fields.
 */
public record BankRequest(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID bankId,
    UUID bankContactId,
    String status,
    Instant submittedAt,
    String contactSnapshot,
    Instant createdAt,
    Instant updatedAt) {}
