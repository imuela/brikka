package com.brika.platform.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * Raw, untrusted rule input as submitted to POST /api/v1/scoring/rulesets — validated by
 * ScoringRulesValidator before ever being persisted. value is a scalar number for most operators,
 * an array of numbers for IN/NOT_IN/BETWEEN.
 */
public record ScoringRuleInput(
    String code, BigDecimal weight, String field, String operator, JsonNode value) {}
