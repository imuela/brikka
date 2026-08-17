package com.brika.platform.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * D10-5: local/testable transport — computes the same structural (never-fabricated) outcome that a
 * real Worker callback would produce, entirely in-process, with no network call. Default active
 * dispatcher so `mvn verify` never depends on a running Python process. Not a simulation of a real
 * AI result — it is honestly the same "no provider" outcome NoOpAiProvider would report.
 */
@Component
@Primary
@ConditionalOnProperty(
    name = "brika.ai.worker-transport",
    havingValue = "local",
    matchIfMissing = true)
public class LocalAiTaskDispatcher implements AiTaskDispatcher {

  private final DocumentExtractionResultHandler resultHandler;

  public LocalAiTaskDispatcher(DocumentExtractionResultHandler resultHandler) {
    this.resultHandler = resultHandler;
  }

  @Override
  public void dispatchDocumentExtraction(
      UUID extractionId, UUID documentVersionId, UUID companyId) {
    resultHandler.applyResult(extractionId, List.of(), Map.of());
  }
}
