package com.brika.platform.bankrequest.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BankResponseResponse(
    UUID id,
    UUID bankRequestId,
    String status,
    Instant receivedAt,
    String summary,
    Map<String, Object> payload,
    Instant createdAt) {}
