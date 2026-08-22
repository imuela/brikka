package com.brika.platform.ai.web;

import java.util.List;
import java.util.Map;

/**
 * Body the AI Worker posts back to the internal callback endpoint. D10-2: a worker that has no
 * provider configured never fabricates a result — {@code provider}/{@code model}/{@code summary}
 * are simply absent (null) and {@code extractedFields} stays empty, exactly as V1 always behaved.
 * Sprint 33: when a worker DID run a real provider, it reports which one via {@code
 * provider}/{@code model} — their presence, not any status field, is what tells
 * DocumentExtractionResultHandler a real result occurred.
 */
public record WorkerCallbackApiRequest(
    List<Map<String, Object>> extractedFields,
    Map<String, Object> confidence,
    String provider,
    String model,
    String summary,
    List<String> warnings) {}
