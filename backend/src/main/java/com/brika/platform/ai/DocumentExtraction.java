package com.brika.platform.ai;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-AI-001 / 21_AI_V1_SCOPE.md §2.A. status PENDING while dispatched, terminal state is
 * NO_PROVIDER (D10-2: structurally completed, no real inference occurred — never SUCCESS/COMPLETED
 * when nothing real happened). provider/model are "none" for the NO_PROVIDER terminal state.
 */
public record DocumentExtraction(
    UUID id,
    UUID companyId,
    UUID documentVersionId,
    String status,
    String provider,
    String model,
    String extractedDataJson,
    String confidenceJson,
    UUID validatedBy,
    Instant validatedAt,
    Instant createdAt) {}
