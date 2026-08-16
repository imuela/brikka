package com.brika.platform.bankmatching;

import java.time.Instant;
import java.util.UUID;

/** ADR-BANKENGINE-001 §9/§10: append-only, never updated after creation (reproducibility). */
public record BankMatchResult(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID bankId,
    UUID bankCriteriaVersionId,
    String globalResult,
    String inputSnapshot,
    UUID evaluatedBy,
    Instant evaluatedAt,
    Instant createdAt) {}
