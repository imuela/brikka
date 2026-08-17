package com.brika.platform.bankmatching;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-BANKENGINE-002 §2/§5: a single, immutable correction of one bank_match_rule_results row.
 * Append-only — never updated after creation. The full audit trail (who, when, why, previous/new
 * value) required by 06_BANK_ENGINE_SPECIFICATION.md §11 is this row itself.
 */
public record BankMatchRuleOverride(
    UUID id,
    UUID companyId,
    UUID bankMatchRuleResultId,
    String previousResult,
    String newResult,
    String reason,
    UUID overriddenBy,
    Instant overriddenAt,
    Instant createdAt) {}
