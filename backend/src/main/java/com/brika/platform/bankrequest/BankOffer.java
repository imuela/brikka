package com.brika.platform.bankrequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** ERD "BANK_OFFER": propuesta de financiación recibida de un banco. */
public record BankOffer(
    UUID id,
    UUID companyId,
    UUID bankRequestId,
    UUID bankId,
    String status,
    BigDecimal amount,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal payment,
    String conditions,
    Instant receivedAt,
    Instant createdAt,
    Instant updatedAt) {}
