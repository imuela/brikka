package com.brika.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Sprint 24 (entornos): PROD arranca fail-closed. {@link ProdEnvironmentValidator} aborta el
 * arranque si falta cualquier secreto o si hay una configuración insegura, y no hace nada cuando el
 * perfil prod no está activo. Unit test sobre MockEnvironment — no arranca contexto (es puro
 * chequeo de entorno).
 */
class ProdEnvironmentValidatorTest {

  private final ProdEnvironmentValidator validator = new ProdEnvironmentValidator();

  @Test
  void doesNothingWhenProdProfileIsNotActive() {
    MockEnvironment env = new MockEnvironment();
    validator.postProcessEnvironment(env, null);
    // no exception, no assertions needed — but confirm it returns without error
    assertThat(env.getActiveProfiles()).isEmpty();
  }

  @Test
  void prodWithoutAnySecretFailsWithAllViolationsListed() {
    MockEnvironment env = prodEnv();

    assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal-signing-key-pem")
        .hasMessageContaining("portal-signing-key-pem")
        .hasMessageContaining("SMTP_HOST")
        .hasMessageContaining("cors-allowed-origins")
        .hasMessageContaining("email-transport=smtp");
  }

  @Test
  void prodWithNoopEmailTransportFails() {
    MockEnvironment env = prodEnv();
    env.setProperty("brika.security.self-auth.internal-signing-key-pem", "k");
    env.setProperty("brika.security.self-auth.portal-signing-key-pem", "k");
    env.setProperty("SMTP_HOST", "smtp.example.com");
    env.setProperty("brika.security.cors-allowed-origins", "https://app.brika.com");

    assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("email-transport=smtp");
  }

  @Test
  void prodWithSeedEnabledFails() {
    MockEnvironment env = prodEnv();
    env.setProperty("brika.security.self-auth.internal-signing-key-pem", "k");
    env.setProperty("brika.security.self-auth.portal-signing-key-pem", "k");
    env.setProperty("SMTP_HOST", "smtp.example.com");
    env.setProperty("brika.security.cors-allowed-origins", "https://app.brika.com");
    env.setProperty("brika.notifications.email-transport", "smtp");
    env.setProperty("brika.seed.enabled", "true");

    assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("seed.enabled=true");
  }

  @Test
  void prodWithWildcardOrLocalhostCorsFails() {
    MockEnvironment env = prodEnv();
    env.setProperty("brika.security.self-auth.internal-signing-key-pem", "k");
    env.setProperty("brika.security.self-auth.portal-signing-key-pem", "k");
    env.setProperty("SMTP_HOST", "smtp.example.com");
    env.setProperty("brika.notifications.email-transport", "smtp");
    env.setProperty(
        "brika.security.cors-allowed-origins", "https://app.brika.com,http://localhost:4200");

    assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("localhost");
  }

  @Test
  void fullyConfiguredProdPasses() {
    MockEnvironment env = prodEnv();
    env.setProperty("brika.security.self-auth.internal-signing-key-pem", "k");
    env.setProperty("brika.security.self-auth.portal-signing-key-pem", "k");
    env.setProperty("SMTP_HOST", "smtp.example.com");
    env.setProperty("SMTP_PORT", "587");
    env.setProperty("brika.notifications.email-transport", "smtp");
    env.setProperty("brika.notifications.email-from", "no-reply@brika.com");
    env.setProperty("brika.security.cors-allowed-origins", "https://app.brika.com");

    // no exception thrown
    validator.postProcessEnvironment(env, null);
    assertThat(env.getProperty("brika.notifications.email-transport")).isEqualTo("smtp");
  }

  private static MockEnvironment prodEnv() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod");
    return env;
  }
}
