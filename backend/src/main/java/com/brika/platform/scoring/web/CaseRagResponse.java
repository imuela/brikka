package com.brika.platform.scoring.web;

import com.brika.platform.scoring.CaseRagIndicator;
import java.util.List;

/**
 * BRIKKA V2 I2. Wire shape of {@code GET /api/v1/cases/{caseId}/scoring/rag}: the combined RAG
 * level plus the per-axis breakdown that justifies it. Enum values travel as their names
 * (GREEN/AMBER/RED/NOT_EVALUATED) — the frontend owns the Spanish labels.
 */
public record CaseRagResponse(String rag, List<Axis> axes) {

  public record Axis(String axis, String level, String detail) {}

  public static CaseRagResponse from(CaseRagIndicator indicator) {
    return new CaseRagResponse(
        indicator.level().name(),
        indicator.axes().stream()
            .map(axis -> new Axis(axis.axis(), axis.level().name(), axis.detail()))
            .toList());
  }
}
