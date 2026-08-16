package com.brika.platform.bankmatching;

import java.time.Instant;
import java.util.UUID;

/** ADR-BANKENGINE-001 §10: append-only, never updated after creation. */
public record BankMatchRuleResult(
    UUID id,
    UUID matchResultId,
    String ruleId,
    String field,
    String operator,
    String expectedValue,
    String evaluatedValue,
    String result,
    String reason,
    Instant createdAt) {}
