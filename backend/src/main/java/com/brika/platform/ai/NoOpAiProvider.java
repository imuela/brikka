package com.brika.platform.ai;

import org.springframework.stereotype.Component;

/**
 * D10-2: no external AI provider is approved (ADR-INTEGRATIONS-001 forbids introducing one without
 * approval). Every call is structurally recorded as not-executed, with an explicit reason, never a
 * fabricated output.
 */
@Component
public class NoOpAiProvider implements AiProvider {

  private static final String REASON =
      "No AI provider configured (Sprint 10 D10-2 — pending business decision to select a"
          + " provider).";

  @Override
  public AiProviderResult summarize(String context) {
    return new AiProviderResult(false, null, REASON);
  }

  @Override
  public AiProviderResult explain(String context) {
    return new AiProviderResult(false, null, REASON);
  }

  @Override
  public AiProviderResult draftMessage(String context) {
    return new AiProviderResult(false, null, REASON);
  }
}
