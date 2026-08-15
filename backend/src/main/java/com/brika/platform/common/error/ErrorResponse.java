package com.brika.platform.common.error;

/**
 * Standard error body, per 05_API_SPECIFICATION.md §5 / 17_API_SPECIFICATION_DETAILED.md §2. Never
 * carries a stack trace or other internal detail.
 */
public record ErrorResponse(String code, String message, String requestId) {}
