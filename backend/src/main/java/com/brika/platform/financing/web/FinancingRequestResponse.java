package com.brika.platform.financing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancingRequestResponse(
    UUID id,
    UUID caseId,
    String status,
    BigDecimal requestedAmount,
    int termMonths,
    Instant createdAt,
    Instant updatedAt) {}
