package com.brika.platform.auth.web;

import com.brika.platform.auth.PasswordResetNotifier;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only stand-in for the real email-backed notifiers that captures the raw token instead of
 * actually sending it, so tests can exercise the confirm step — the real API never returns the raw
 * token anywhere (autorización §11). Two {@code @Qualifier}-matched beans are needed since Sprint
 * 22 cierre wires {@code UserAuthenticationService}/{@code PortalAuthenticationService} against
 * distinct qualified {@code PasswordResetNotifier} beans (ADR-PORTAL-AUTH-001 — no shared
 * implementation between the two realms), both backed by the same capture so a test can assert on
 * either path without knowing in advance which one fired.
 */
@TestConfiguration
public class CapturingPasswordResetNotifierConfig {

  public record Captured(String email, String rawToken) {}

  private final AtomicReference<Captured> lastCaptured = new AtomicReference<>();

  @Bean
  @Primary
  @Qualifier("userPasswordResetNotifier")
  public PasswordResetNotifier capturingUserPasswordResetNotifier() {
    return (email, rawToken) -> lastCaptured.set(new Captured(email, rawToken));
  }

  @Bean
  @Primary
  @Qualifier("portalPasswordResetNotifier")
  public PasswordResetNotifier capturingPortalPasswordResetNotifier() {
    return (email, rawToken) -> lastCaptured.set(new Captured(email, rawToken));
  }

  public Captured lastCaptured() {
    return lastCaptured.get();
  }

  /**
   * The beans are Spring-context-scoped singletons, reused across every test method in the class —
   * callers must reset in @BeforeEach or an earlier test's capture leaks into a later "nothing
   * captured" assertion.
   */
  public void reset() {
    lastCaptured.set(null);
  }
}
