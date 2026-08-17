package com.brika.platform.ai;

/**
 * Seam between "the domain needs an AI-generated result" and "which provider/model actually
 * produces it". Sprint 10 (D10-2): no external provider is approved — only NoOpAiProvider exists.
 * Swapping in a real provider later is a configuration change behind this interface, no caller
 * changes. Deliberately abstract: the three synchronous use cases (summarize/explain/draftMessage)
 * share one contract since none of them requires the Python Worker (only document extraction does —
 * see AiTaskDispatcher).
 */
public interface AiProvider {

  AiProviderResult summarize(String context);

  AiProviderResult explain(String context);

  AiProviderResult draftMessage(String context);
}
