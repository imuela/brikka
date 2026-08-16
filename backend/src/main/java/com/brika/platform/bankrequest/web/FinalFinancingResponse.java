package com.brika.platform.bankrequest.web;

import java.time.Instant;
import java.util.UUID;

public record FinalFinancingResponse(
    UUID id,
    UUID caseId,
    UUID bankOfferId,
    String status,
    Instant finalizedAt,
    Instant createdAt,
    Instant updatedAt) {}
