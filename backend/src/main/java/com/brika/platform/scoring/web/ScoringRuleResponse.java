package com.brika.platform.scoring.web;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoringRuleResponse(
    UUID id, String code, BigDecimal weight, String field, String operator, Object value) {}
