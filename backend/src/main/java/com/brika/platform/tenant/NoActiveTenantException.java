package com.brika.platform.tenant;

/** Thrown when access to a tenant-owned resource is attempted without a resolved tenant. */
public class NoActiveTenantException extends RuntimeException {

  public NoActiveTenantException(String message) {
    super(message);
  }
}
