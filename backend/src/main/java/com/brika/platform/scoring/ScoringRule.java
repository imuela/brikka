package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Row of the normalized scoring_rules table. configurationJson holds the {field, operator, value}
 * condition (ADR-SCORING-001 D9-2).
 */
public record ScoringRule(
    UUID id, UUID rulesetId, String code, BigDecimal weight, String configurationJson) {}
