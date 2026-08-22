package com.brika.platform.ai.web;

import com.brika.platform.ai.DocumentExtractionResultHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-AI-001: "endpoint interno de callback en Spring Boot fuera de /api/v1 público" — deliberately
 * outside /api/v1, never reachable via the internal JWT-authenticated filter chain's public
 * surface. Protected instead by a shared secret configured out-of-band (brika.ai.worker-callback-
 * secret), checked manually here since this is not a user-facing endpoint and does not belong in
 * the JWT-based SecurityConfig chains. Only the real HttpAiTaskDispatcher path ever calls this in
 * practice — LocalAiTaskDispatcher (the active default, D10-5) bypasses HTTP entirely.
 */
@RestController
public class AiExtractionCallbackController {

  private final DocumentExtractionResultHandler resultHandler;
  private final String configuredSecret;

  public AiExtractionCallbackController(
      DocumentExtractionResultHandler resultHandler,
      @Value("${brika.ai.worker-callback-secret:}") String configuredSecret) {
    this.resultHandler = resultHandler;
    this.configuredSecret = configuredSecret;
  }

  @PostMapping("/internal/ai/document-extractions/{id}/callback")
  public void callback(
      @PathVariable UUID id,
      @RequestHeader(value = "X-Ai-Worker-Secret", required = false) String providedSecret,
      @RequestBody WorkerCallbackApiRequest request) {
    if (configuredSecret.isBlank() || !secretMatches(providedSecret)) {
      throw new AccessDeniedException("Invalid or missing worker callback secret.");
    }
    resultHandler.applyResult(
        id,
        request.extractedFields(),
        request.confidence(),
        request.provider(),
        request.model(),
        request.summary(),
        request.warnings());
  }

  /**
   * Sprint 12 D12-5.2: constant-time comparison ({@link MessageDigest#isEqual}) instead of {@code
   * String.equals}, which short-circuits on the first differing byte and can leak timing
   * information about the secret. {@code providedSecret} may be {@code null} (header optional) —
   * guarded explicitly since {@code MessageDigest.isEqual} does not accept null arguments.
   */
  private boolean secretMatches(String providedSecret) {
    if (providedSecret == null) {
      return false;
    }
    return MessageDigest.isEqual(
        configuredSecret.getBytes(StandardCharsets.UTF_8),
        providedSecret.getBytes(StandardCharsets.UTF_8));
  }
}
