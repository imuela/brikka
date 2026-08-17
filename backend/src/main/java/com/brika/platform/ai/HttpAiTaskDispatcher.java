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

  public HttpAiTaskDispatcher(
      ObjectMapper objectMapper,
      @Value("${brika.ai.worker-url:http://localhost:8100}") String workerBaseUrl,
      @Value("${brika.ai.callback-base-url:http://localhost:8080}") String callbackBaseUrl,
      @Value("${brika.ai.worker-callback-secret:}") String sharedSecret) {
    this.objectMapper = objectMapper;
    this.workerBaseUrl = workerBaseUrl;
    this.callbackBaseUrl = callbackBaseUrl;
    this.sharedSecret = sharedSecret;
  }

  @Override
  public void dispatchDocumentExtraction(
      UUID extractionId, UUID documentVersionId, UUID companyId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("documentVersionId", documentVersionId.toString());
    payload.put(
        "callbackUrl",
        callbackBaseUrl + "/internal/ai/document-extractions/" + extractionId + "/callback");
    payload.put("callbackSecret", sharedSecret);

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
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to dispatch document extraction to AI Worker", e);
    }
  }
}
