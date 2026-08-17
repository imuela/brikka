package com.brika.platform.scoring.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScoringResultResponse(
    UUID id,
    UUID caseId,
    UUID rulesetId,
    BigDecimal totalScore,
    String category,
    Object explanation,
    Instant calculatedAt) {}
