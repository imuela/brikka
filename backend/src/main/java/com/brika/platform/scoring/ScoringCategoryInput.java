package com.brika.platform.scoring;

import java.math.BigDecimal;

/**
 * Raw, untrusted category input as submitted to POST /api/v1/scoring/rulesets — validated by
 * ScoringRulesValidator before ever being persisted. maxScore is null for the catch-all entry.
 */
public record ScoringCategoryInput(String name, BigDecimal maxScore) {}
