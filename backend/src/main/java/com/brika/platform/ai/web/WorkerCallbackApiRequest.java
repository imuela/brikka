package com.brika.platform.ai.web;

import java.util.List;
import java.util.Map;

/**
 * Body the AI Worker posts back to the internal callback endpoint. D10-2: the worker never
 * fabricates a result, so V1 callers always report an empty extraction with status NO_PROVIDER —
 * this shape exists so a real worker has a well-defined contract to fill in later.
 */
public record WorkerCallbackApiRequest(
    List<Map<String, Object>> extractedFields, Map<String, Object> confidence) {}
