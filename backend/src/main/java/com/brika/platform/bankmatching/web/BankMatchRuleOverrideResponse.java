package com.brika.platform.bankmatching.web;

import java.time.Instant;
import java.util.UUID;

public record BankMatchRuleOverrideResponse(
    UUID id,
    String previousResult,
    String newResult,
    String reason,
    UUID overriddenBy,
    Instant overriddenAt) {}
