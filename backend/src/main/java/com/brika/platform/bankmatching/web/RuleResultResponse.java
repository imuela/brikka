package com.brika.platform.bankmatching.web;

import java.util.List;
import java.util.UUID;

public record RuleResultResponse(
    UUID id,
    String ruleId,
    String field,
    String operator,
    Object expectedValue,
    Object evaluatedValue,
    String result,
    String reason,
    String effectiveResult,
    int overrideCount,
    List<BankMatchRuleOverrideResponse> overrides) {}
