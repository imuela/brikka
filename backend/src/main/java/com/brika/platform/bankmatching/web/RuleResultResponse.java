package com.brika.platform.bankmatching.web;

public record RuleResultResponse(
    String ruleId,
    String field,
    String operator,
    Object expectedValue,
    Object evaluatedValue,
    String result,
    String reason) {}
