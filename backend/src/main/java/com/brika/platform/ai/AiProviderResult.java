package com.brika.platform.ai;

/**
 * Outcome of one direct AI provider call (summarize/explain/draftMessage). D10-2: never claims
 * success when no real provider executed.
 */
public record AiProviderResult(boolean executed, String output, String reason) {}
