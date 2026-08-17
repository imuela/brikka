package com.brika.platform.ai.web;

/**
 * Shared request shape for the three synchronous AI use cases (summarize/explain/draftMessage).
 * D10-2: no business rule defines what "context" must contain — the caller supplies it explicitly;
 * NoOpAiProvider never inspects it.
 */
public record AiUseCaseApiRequest(String context) {}
