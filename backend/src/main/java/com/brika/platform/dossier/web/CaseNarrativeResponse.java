package com.brika.platform.dossier.web;

import com.brika.platform.dossier.CaseNarrative;
import java.util.List;

/**
 * BRIKKA V2 I5. Wire shape of {@code GET /api/v1/cases/{caseId}/dossier/narrative}: the same
 * deterministic narrative the dossier HTML embeds, as structured JSON for the case-detail view.
 */
public record CaseNarrativeResponse(List<Section> sections) {

  public record Section(String key, String title, List<String> paragraphs) {}

  public static CaseNarrativeResponse from(CaseNarrative narrative) {
    return new CaseNarrativeResponse(
        narrative.sections().stream()
            .map(section -> new Section(section.key(), section.title(), section.paragraphs()))
            .toList());
  }
}
