package com.brika.platform.scoring.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScoringRulesetResponse(
    UUID id,
    String code,
    String version,
    String status,
    Object categories,
    List<ScoringRuleResponse> rules,
    Instant createdAt) {}
