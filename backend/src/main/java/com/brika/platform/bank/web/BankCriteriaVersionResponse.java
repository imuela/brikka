package com.brika.platform.bank.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BankCriteriaVersionResponse(
    UUID id,
    UUID bankId,
    String version,
    String status,
    Instant effectiveFrom,
    Instant effectiveTo,
    Map<String, Object> rules) {}
