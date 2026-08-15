package com.brika.platform.common.error;

import com.brika.platform.common.observability.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Baseline error handling for Sprint 1: any unhandled exception is logged server-side (with stack
 * trace) and answered with the standard error body, never a stack trace to the client. No business
 * exception types exist yet — those arrive with the feature that needs them.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    log.error("Unhandled exception (requestId={})", requestId, exception);
    ErrorResponse body =
        new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.", requestId);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
