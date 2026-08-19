package com.brika.platform.common.error;

import com.brika.platform.auth.AuthenticationFailedException;
import com.brika.platform.auth.InvalidRefreshTokenException;
import com.brika.platform.auth.TooManyLoginAttemptsException;
import com.brika.platform.common.observability.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Baseline error handling: any unhandled exception is logged server-side (with stack trace) and
 * answered with the standard error body, never a stack trace to the client.
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

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    ErrorResponse body = new ErrorResponse(exception.code(), exception.getMessage(), requestId);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
