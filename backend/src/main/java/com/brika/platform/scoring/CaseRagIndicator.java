package com.brika.platform.scoring;

import java.util.List;

/**
 * BRIKKA V2 I2. Deterministic qualitative RAG indicator of a case: the combined {@code level} is
 * the worst of the axes that could be evaluated, or {@code NOT_EVALUATED} when none could. It
 * introduces no new business variable — every axis is derived from data the platform already stores
 * (scoring results, viability analysis, document checklist).
 */
public record CaseRagIndicator(RagLevel level, List<RagAxis> axes) {}
