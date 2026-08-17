package com.brika.platform.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Traceability record for one AI operation (21_AI_V1_SCOPE.md §8). provider/model are "none" when
 * NoOpAiProvider handled the call (D10-2) — never a fabricated real value.
 */
public record AiUsage(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID userId,
    String provider,
    String model,
    String operation,
    Integer inputTokens,
    Integer outputTokens,
    BigDecimal estimatedCost,
    Instant createdAt) {}
