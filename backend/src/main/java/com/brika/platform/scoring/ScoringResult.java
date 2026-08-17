package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ADR-SCORING-001: append-only, never updated after creation (reproducibility). explanationJson
 * embeds both the input snapshot and the per-rule breakdown — scoring_results has no separate
 * snapshot column, so both live inside the single `explanation` jsonb column.
 */
public record ScoringResult(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID rulesetId,
    BigDecimal totalScore,
    String category,
    String explanationJson,
    Instant calculatedAt) {}
