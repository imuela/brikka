package com.brika.platform.bankrequest.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BankOfferResponse(
    UUID id,
    UUID bankRequestId,
    UUID bankId,
    String status,
    BigDecimal amount,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal payment,
    Map<String, Object> conditions,
    Instant receivedAt,
    Instant createdAt,
    Instant updatedAt) {}
