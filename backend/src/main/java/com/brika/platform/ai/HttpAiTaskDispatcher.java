package com.brika.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * D10-5: real transport to an actual running Python Worker over plain HTTP — a stand-in for the
 * RabbitMQ wiring ADR-AI-001 describes, since 20_RABBITMQ_SPECIFICATION.md names only the event
 * type (`ai.document.analysis.requested`) and a generic envelope, not exchange/queue/routing-key
 * names. The envelope fields reused here are exactly the ones the spec does define. Not the active
 * default (see LocalAiTaskDispatcher) — activate with `brika.ai.worker-transport=http` once a
 * Worker is actually deployed and reachable.
 *
 * <p>Sprint 35: {@code dispatchTimeoutSeconds} found and fixed via the first real (non-mocked)
 * Ollama inference on this project's dev hardware. The Worker's own request handling is fully
 * synchronous end-to-end (fetch document → call the AI provider → POST the callback → THEN respond
 * to this dispatch call), so this HTTP call effectively blocks for as long as the provider call
 * takes. The old hardcoded 10s timeout was never wrong for the paths it had actually been exercised
 * against (NO_PROVIDER responds instantly; Anthropic's cloud API typically returns in a few
 * seconds) — it was simply never reconciled with {@code ai-worker/main.py}'s own {@code
 * OLLAMA_REQUEST_TIMEOUT_SECONDS}, which already anticipated slower local CPU inference. Real
 * extraction on this machine's decade-old Intel CPU (no GPU) genuinely exceeded 10s (a quick ping)
 * and then also exceeded the Worker's first-pass 60s allowance (a real document-extraction prompt)
 * — both raised together from measured timing, not guessed: Worker allowance to 120s, this dispatch
 * ceiling to 150s (comfortably above it plus document-fetch/callback overhead), kept configurable
 * since a deployment that only ever uses a fast cloud provider may reasonably want a shorter one.
 */
@Component
@ConditionalOnProperty(name = "brika.ai.worker-transport", havingValue = "http")
public class HttpAiTaskDispatcher implements AiTaskDispatcher {

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper;
  private final String workerBaseUrl;
  private final String callbackBaseUrl;
  private final String sharedSecret;
  private final Duration dispatchTimeout;

  public HttpAiTaskDispatcher(
      ObjectMapper objectMapper,
      @Value("${brika.ai.worker-url:http://localhost:8100}") String workerBaseUrl,
      @Value("${brika.ai.callback-base-url:http://localhost:8080}") String callbackBaseUrl,
      @Value("${brika.ai.worker-callback-secret:}") String sharedSecret,
      @Value("${brika.ai.worker-dispatch-timeout-seconds:150}") long dispatchTimeoutSeconds) {
    this.objectMapper = objectMapper;
    this.workerBaseUrl = workerBaseUrl;
    this.callbackBaseUrl = callbackBaseUrl;
    this.sharedSecret = sharedSecret;
    this.dispatchTimeout = Duration.ofSeconds(dispatchTimeoutSeconds);
  }

  @Override
  public void dispatchDocumentExtraction(
      UUID extractionId,
      UUID documentVersionId,
      UUID companyId,
      DocumentDownloadContext downloadContext) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("documentVersionId", documentVersionId.toString());
    payload.put(
        "callbackUrl",
        callbackBaseUrl + "/internal/ai/document-extractions/" + extractionId + "/callback");
    payload.put("callbackSecret", sharedSecret);
    if (downloadContext != null) {
      payload.put("documentDownloadUrl", downloadContext.downloadUrl().toString());
      payload.put("documentFilename", downloadContext.filename());
      payload.put("documentMimeType", downloadContext.mimeType());
    }

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", UUID.randomUUID().toString());
    envelope.put("eventType", "ai.document.analysis.requested");
    envelope.put("occurredAt", Instant.now().toString());
    envelope.put("companyId", companyId.toString());
    envelope.put("aggregateType", "DOCUMENT_EXTRACTION");
    envelope.put("aggregateId", extractionId.toString());
    envelope.put("payload", payload);

    try {
      String body = objectMapper.writeValueAsString(envelope);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(workerBaseUrl + "/extract"))
              .header("Content-Type", "application/json")
              .timeout(dispatchTimeout)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to dispatch document extraction to AI Worker", e);
    }
  }
}
