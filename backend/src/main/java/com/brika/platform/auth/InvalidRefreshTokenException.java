package com.brika.platform.auth;

/**
 * Thrown by both {@link UserRefreshTokenService} and {@link PortalRefreshTokenService} — a plain
 * technical reason code with no identity data, so sharing this one exception type does not blur the
 * ADR-PORTAL-AUTH-001 boundary between the two domains (each service still resolves its own table,
 * its own principal type).
 */
public class InvalidRefreshTokenException extends RuntimeException {

  public enum Reason {
    UNKNOWN,
    EXPIRED,
    REVOKED,
    REUSED
  }

  private final Reason reason;

  public InvalidRefreshTokenException(Reason reason) {
    super("Invalid refresh token: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
