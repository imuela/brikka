package com.brika.platform.bankrequest.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BankRequestResponse(
    UUID id,
    UUID caseId,
    UUID bankId,
    UUID bankContactId,
    String status,
    Instant submittedAt,
    Map<String, Object> contactSnapshot,
    Instant createdAt,
    Instant updatedAt) {}
