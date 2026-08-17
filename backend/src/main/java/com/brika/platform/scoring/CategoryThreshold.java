package com.brika.platform.scoring;

import java.math.BigDecimal;

/**
 * ADR-SCORING-001 D9-3: one entry of a ruleset's category configuration. maxScore is null for the
 * final catch-all entry only.
 */
public record CategoryThreshold(String name, BigDecimal maxScore) {}
