package com.brika.platform.common.error;

import com.brika.platform.auth.AuthenticationFailedException;
import com.brika.platform.auth.InvalidRefreshTokenException;
import com.brika.platform.auth.TooManyLoginAttemptsException;
import com.brika.platform.common.observability.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Baseline error handling: any unhandled exception is logged server-side (with stack trace) and
 * answered with the standard error body, never a stack trace to the client.
 *
 * <p>Sprint 29 (stabilization): {@link NoResourceFoundException} and {@link
 * DataIntegrityViolationException} used to fall through to {@link #handleUnexpected}, answering an
 * unmapped route or a caller-triggered DB constraint violation (missing required field, value too
 * long, etc.) with the same generic 500 as a real internal failure. Both are caller-input problems,
 * not server bugs, so they get their own handlers below with the HTTP status that actually matches
 * the cause. Domain code should still validate proactively (see e.g. CaseService.cancel,
 * DocumentRequestService.create) — the constraint-violation handler here is a backstop for whatever
 * a specific validation missed, not the primary defense.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse("FORBIDDEN", "Access denied.", requestId);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  @ExceptionHandler(AuthenticationFailedException.class)
  public ResponseEntity<ErrorResponse> handleAuthenticationFailed(
      AuthenticationFailedException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse("UNAUTHENTICATED", "Invalid credentials.", requestId);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
      InvalidRefreshTokenException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse("UNAUTHENTICATED", "Invalid refresh token.", requestId);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(TooManyLoginAttemptsException.class)
  public ResponseEntity<ErrorResponse> handleTooManyLoginAttempts(
      TooManyLoginAttemptsException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body =
        new ErrorResponse("TOO_MANY_ATTEMPTS", "Too many login attempts.", requestId);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse(exception.code(), exception.getMessage(), requestId);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(ValidationException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse(exception.code(), exception.getMessage(), requestId);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  // Sprint 40 audit: a missing or malformed @RequestBody (any of the 32 endpoints that declare
  // one) fell through to handleUnexpected below, answering a client-input problem with the same
  // generic 500 as a real server bug. Same reasoning as the DataIntegrityViolationException
  // handler above — a caller mistake, not a server failure, gets the status that matches the
  // cause. No @Valid/Bean Validation exists in this codebase yet, so this covers the actual gap
  // without inventing a broader validation-error contract that nothing currently needs.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body =
        new ErrorResponse(
            "INVALID_REQUEST", "The request body is missing or malformed.", requestId);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse(exception.code(), exception.getMessage(), requestId);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse("NOT_FOUND", "Resource not found.", requestId);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    log.warn("Data integrity violation (requestId={})", requestId, exception);
    ErrorResponse body =
        new ErrorResponse(
            "INVALID_REQUEST",
            "The request could not be completed because it violates a data constraint (e.g. a"
                + " required field is missing or a value is too long).",
            requestId);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    log.error("Unhandled exception (requestId={})", requestId, exception);
    ErrorResponse body =
        new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.", requestId);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
