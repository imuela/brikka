package com.brika.platform.bankmatching.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BankMatchResultResponse(
    UUID id,
    UUID caseId,
    UUID bankId,
    UUID bankCriteriaVersionId,
    String globalResult,
    String effectiveGlobalResult,
    Instant evaluatedAt,
    Object inputSnapshot,
    List<RuleResultResponse> ruleResults) {}
