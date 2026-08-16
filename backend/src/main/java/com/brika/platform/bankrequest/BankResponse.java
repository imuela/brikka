package com.brika.platform.bankrequest;

import java.time.Instant;
import java.util.UUID;

/** ERD "BANK_RESPONSE": respuesta de una entidad a una solicitud. */
public record BankResponse(
    UUID id,
    UUID bankRequestId,
    String status,
    Instant receivedAt,
    String summary,
    String payload,
    Instant createdAt) {}
