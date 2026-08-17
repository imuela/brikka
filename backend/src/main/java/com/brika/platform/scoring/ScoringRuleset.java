package com.brika.platform.scoring;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-SCORING-001 D9-6: global, no company_id — same pattern as bank_criteria_versions. The `rules`
 * column (jsonb, table scoring_rulesets) holds the ruleset-level category configuration —
 * individual rules live in the normalized scoring_rules table (see ScoringRule).
 */
public record ScoringRuleset(
    UUID id,
    String code,
    String version,
    String status,
    String categoriesJson,
    Instant createdAt) {}
