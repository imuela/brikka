package com.brika.platform.common.error;

/**
 * Optimistic-concurrency conflict: the caller's expected prior state is stale. Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {

  private final String code;

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
