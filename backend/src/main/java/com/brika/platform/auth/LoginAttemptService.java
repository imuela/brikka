package com.brika.platform.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sliding-window lockout against brute force / credential stuffing / password spraying
 * (autorización Sprint 22 §11). Identifier is whatever the login form was given (email), lowered to
 * a canonical form by the caller before recording/checking — this service never looks up whether
 * the identifier actually corresponds to an account, by design.
 */
@Service
public class LoginAttemptService {

  public static final String REALM_INTERNAL = "internal";
  public static final String REALM_PORTAL = "portal";

  private final LoginAttemptRepository repository;
  private final Duration window;
  private final int maxFailedAttempts;

  public LoginAttemptService(
      LoginAttemptRepository repository,
      @Value("${brika.security.self-auth.login-attempt-window-seconds:900}") long windowSeconds,
      @Value("${brika.security.self-auth.login-attempt-max-failures:5}") int maxFailedAttempts) {
    this.repository = repository;
    this.window = Duration.ofSeconds(windowSeconds);
    this.maxFailedAttempts = maxFailedAttempts;
  }

  public boolean isLocked(String realm, String identifier) {
    return repository.countFailedSince(realm, identifier, Instant.now().minus(window))
        >= maxFailedAttempts;
  }

  public void recordFailure(String realm, String identifier) {
    repository.record(realm, identifier, false);
  }

  public void recordSuccess(String realm, String identifier) {
    repository.record(realm, identifier, true);
  }
}
