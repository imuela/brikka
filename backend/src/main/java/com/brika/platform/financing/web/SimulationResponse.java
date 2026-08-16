package com.brika.platform.financing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SimulationResponse(
    UUID id,
    UUID caseId,
    BigDecimal principal,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal estimatedPayment,
    Map<String, Object> metadata,
    UUID createdBy,
    Instant createdAt) {}
