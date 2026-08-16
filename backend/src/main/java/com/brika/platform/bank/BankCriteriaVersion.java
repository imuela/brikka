package com.brika.platform.bank;

import java.time.Instant;
import java.util.UUID;

/** rules is stored/versioned only — Sprint 5 does not interpret or execute it (Sprint 6 scope). */
public record BankCriteriaVersion(
    UUID id,
    UUID bankId,
    String version,
    String status,
    Instant effectiveFrom,
    Instant effectiveTo,
    String rules) {}
