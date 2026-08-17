package com.brika.platform.ai;

import java.util.UUID;

/**
 * Seam between "a document extraction was requested" and "how the Python Worker actually receives
 * it". D10-5: 20_RABBITMQ_SPECIFICATION.md names the event (`ai.document.analysis.requested`) and a
 * generic envelope, but gives no exchange/queue/routing-key names — insufficient to build real
 * RabbitMQ wiring without inventing infrastructure names, which is explicitly forbidden. Real
 * RabbitMQ wiring therefore remains explicit pending integration work (not implemented here, not
 * silently faked). LocalAiTaskDispatcher (default) is the local/testable implementation required by
 * D10-5; HttpAiTaskDispatcher is real, working code that talks to an actual running Worker over
 * HTTP, but is not the active default (keeps `mvn verify` self-contained, no Python process
 * dependency).
 */
public interface AiTaskDispatcher {

  void dispatchDocumentExtraction(UUID extractionId, UUID documentVersionId, UUID companyId);
}
