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
        .hasMessageContaining("MINIO_ROOT_USER")
        .hasMessageContaining("MINIO_ROOT_PASSWORD")
        .hasMessageContaining("RABBITMQ_USER")
        .hasMessageContaining("RABBITMQ_PASSWORD")
        .hasMessageContaining("DB_USER")
        .hasMessageContaining("DB_PASSWORD")
        .hasMessageContaining("email-transport=smtp");
  }

  @Test
  void prodWithoutStorageBrokerOrDbCredentialsFailsEvenIfEverythingElseIsSet() {
    // D39-5: brika.storage.access-key/secret-key, spring.rabbitmq.username/password, and
    // spring.datasource.username/password all resolve to a non-blank default
    // ("brika"/"brika_dev_password") from application.yml even when the real env var is unset,
    // so this must fail on the raw MINIO_ROOT_*/RABBITMQ_*/DB_* env vars, not on the
    // always-non-blank resolved property.
    MockEnvironment env = prodEnv();
    env.setProperty("brika.security.self-auth.internal-signing-key-pem", "k");
    env.setProperty("brika.security.self-auth.portal-signing-key-pem", "k");
    env.setProperty("SMTP_HOST", "smtp.example.com");
    env.setProperty("brika.notifications.email-transport", "smtp");
    env.setProperty("brika.security.cors-allowed-origins", "https://app.brika.com");

    assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MINIO_ROOT_USER")
        .hasMessageContaining("MINIO_ROOT_PASSWORD")
        .hasMessageContaining("RABBITMQ_USER")
        .hasMessageContaining("RABBITMQ_PASSWORD")
        .hasMessageContaining("DB_USER")
        .hasMessageContaining("DB_PASSWORD");
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
    env.setProperty("MINIO_ROOT_USER", "s3-user");
    env.setProperty("MINIO_ROOT_PASSWORD", "s3-password");
    env.setProperty("RABBITMQ_USER", "broker-user");
    env.setProperty("RABBITMQ_PASSWORD", "broker-password");
    env.setProperty("DB_USER", "db-user");
    env.setProperty("DB_PASSWORD", "db-password");

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
