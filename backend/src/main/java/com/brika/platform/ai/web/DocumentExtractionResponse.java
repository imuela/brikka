package com.brika.platform.ai.web;

import java.time.Instant;
import java.util.UUID;

public record DocumentExtractionResponse(
    UUID id,
    UUID documentVersionId,
    String status,
    String provider,
    String model,
    Object extractedData,
    Object confidence,
    UUID validatedBy,
    Instant validatedAt,
    Instant createdAt) {}
