package com.brika.platform.common.error;

/**
 * Also used to mask cross-tenant existence: a resource that exists in another tenant is reported
 * the same as one that does not exist at all, never as 403.
 */
public class ResourceNotFoundException extends RuntimeException {

  private final String code;

  public ResourceNotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
